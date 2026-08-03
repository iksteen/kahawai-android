package com.kolktech.kahawai.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kolktech.kahawai.data.network.ApiClient
import com.kolktech.kahawai.data.network.dto.StartSessionResponse
import com.kolktech.kahawai.data.network.dto.SubtitleTrack
import com.kolktech.kahawai.data.network.readableMessage
import com.kolktech.kahawai.data.repository.PlaybackRepository
import com.kolktech.kahawai.playback.CapabilityProfileBuilder
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface PlayerState {
    data object Loading : PlayerState
    data class Error(val message: String) : PlayerState
    data object Ready : PlayerState
}

/// What an "ass"/"overlay" delivery renderer needs to tap the hub's
/// out-of-band session streams (subs-{id}.ass / subs-{id}.jsonl):
/// [streamBaseUrl] is [session.streamUrl] with the last path segment
/// stripped, mirroring web/src/views/Player.tsx's `base`. [streamBaseUrl]
/// itself doesn't change across a seek-restart within one session, so
/// [epoch] is bumped on every attach() to give overlay composables an
/// explicit key to reconnect their stream on. Timing deliberately does
/// NOT travel through here: overlays read the absolute video position
/// straight off [PlayerViewModel.player], whose position overrides
/// already fold in offsetMs — including [PlayerViewModel.syncOrigin]'s
/// in-place correction — in every mode. (An earlier revision carried
/// offsetMs in this class and had overlays add it to player position
/// themselves; once the ForwardingPlayer's getCurrentPosition() started
/// reporting absolute time for the seekbar fix, that added the offset
/// TWICE, desyncing ass/overlay subtitles after every seek or mid-video
/// resume.)
/// [isHls] mirrors web/src/views/Player.tsx's `isHls`: the live
/// `subs-{id}.ass` tap only exists for Remux/Transcode sessions — the
/// hub's session_file handler no-ops it for Mode::Direct (the raw file
/// is served byte-for-byte with no server-side pipeline to tap), so a
/// direct-mode session must fall back straight to the item-scoped
/// `/items/{id}/subtitles/{id}.ass` whole-file-extraction endpoint
/// instead (see AssSubtitleOverlay).
data class SubtitleSession(
    val streamBaseUrl: String,
    val isHls: Boolean,
    val epoch: Int,
)

/// Progress cadence matches the web client (web/src/views/Player.tsx:678-710):
/// every 10s while playing, plus on pause and on teardown.
private const val PROGRESS_INTERVAL_MS = 10_000L

private const val TAG = "PlayerViewModel"

/// See PlayerViewModel.buildMediaSourceFactory. Media3's own default is
/// 3.5 (DefaultHlsPlaylistTracker.DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT).
///
/// A raised-but-still-finite coefficient (30.0, ~60s) was tried first and
/// still failed on a seek far ahead — but the SAME hub, same seek, same
/// content played correctly in the web client, which proves the hub's
/// pipeline isn't the problem. web/src/views/Player.tsx's hls.js config
/// doesn't give its analogous check a longer timeout either — it disables
/// it outright: `liveMaxLatencyDurationCount: Infinity`. This mirrors
/// that exactly rather than guessing at a bigger-but-still-wrong finite
/// number a second time. (now - lastSnapshotChangeMs) > targetDurationMs
/// * COEFFICIENT: multiplying by Double.MAX_VALUE overflows the right
/// side to +Infinity under IEEE 754 (no crash, no NaN), and elapsed real
/// time is never greater than infinity, so the check can never fire.
private const val HLS_PLAYLIST_STUCK_COEFFICIENT = Double.MAX_VALUE

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val repo: PlaybackRepository,
    val itemId: String,
    private val startMs: Long,
    private val initialAudioTrack: Int = 0,
    private val initialSubtitleTrackId: Long? = null,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    val state: StateFlow<PlayerState> = _state

    /// Failures that DON'T end the session — a failed seek or subtitle
    /// switch leaves the current stream playable, so they surface as a
    /// snackbar over the still-running player rather than tearing the
    /// whole UI down to [PlayerState.Error] (which is reserved for
    /// "nothing is playing and nothing will": start() and onPlayerError).
    private val _transientError = MutableStateFlow<String?>(null)
    val transientError: StateFlow<String?> = _transientError

    fun clearTransientError() {
        _transientError.value = null
    }

    private val realPlayer: ExoPlayer by lazy {
        val dataSourceFactory = DefaultDataSource.Factory(
            getApplication(),
            OkHttpDataSource.Factory(ApiClient.streamingOkHttpClient()),
        )
        ExoPlayer.Builder(getApplication())
            .setMediaSourceFactory(buildMediaSourceFactory(dataSourceFactory))
            // handleAudioFocus=true so ExoPlayer pauses itself on transient
            // audio focus loss (e.g. an incoming call ringing) and resumes
            // when focus is returned, without us wiring up an AudioManager
            // listener by hand.
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .build()
            .apply {
                // Text tracks stay enabled but with no override selected —
                // "off" by default. Sideloaded VTT (Text delivery) configs
                // are attached per-MediaItem in attach(); switching between
                // them is a TrackSelectionOverride (selectSubtitleTrack
                // below), same mechanism as showAudioTrackMenu. Ass/Overlay
                // delivery never touches this pipeline at all — those are
                // out-of-band taps rendered by AssSubtitleOverlay /
                // ImageSubtitleOverlay.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .build()
            }
    }

    /// Media3's default HLS playlist-stuck tolerance
    /// (DefaultHlsPlaylistTracker.DEFAULT_PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT,
    /// 3.5×) is tuned for a live encoder that's already running and just
    /// pauses briefly — at the hub's 2s segment target-duration, that's
    /// only ~7s before ExoPlayer gives up with a PlaylistStuckException.
    /// A seek-restart here isn't a brief pause: the hub tears down and
    /// restarts the WHOLE remux/transcode pipeline at a new position
    /// first (confirmed by seeking far ahead in the field — the hub's
    /// own seek accepted the request and reported a new part_base_ms,
    /// but nothing had reached the playlist by the time ExoPlayer gave
    /// up), which can legitimately take longer than 7s, especially
    /// seeking deep into a large file. 30× (~60s here) gives the hub
    /// realistic room to finish restarting while still eventually
    /// failing loudly — surfacing as PlayerState.Error, not a silent
    /// black screen — if something really is broken.
    ///
    /// DefaultMediaSourceFactory has no hook to reach into the HLS
    /// delegate it builds internally, so this constructs the HLS branch
    /// by hand and falls back to a plain DefaultMediaSourceFactory for
    /// "direct" mode's progressive/byte-range files.
    ///
    /// DefaultMediaSourceFactory.createMediaSource() does two things for
    /// any content type: builds the content MediaSource via whichever
    /// delegate matches, THEN separately wraps each of MediaItem's own
    /// sideloaded subtitleConfigurations (Text-delivery VTT — see
    /// textDeliverySubtitleConfigs()) and merges them all with
    /// MergingMediaSource. That merging step lives in
    /// DefaultMediaSourceFactory itself, NOT in HlsMediaSource.Factory
    /// — calling hlsFactory.createMediaSource(mediaItem) directly (as
    /// this did before) skips it entirely, silently dropping every
    /// Text-delivery subtitle track for remux/transcode sessions. Ass/
    /// Overlay delivery is unaffected either way (out-of-band HTTP taps,
    /// never routed through MediaItem at all), which is why this
    /// regression was easy to miss.
    ///
    /// The wrapping MUST be the modern ProgressiveMediaSource +
    /// SubtitleExtractor arrangement (what DefaultMediaSourceFactory
    /// itself builds — confirmed by disassembling its 1.10.0
    /// createMediaSource), NOT the legacy SingleSampleMediaSource this
    /// used before: SingleSampleMediaSource delivers the raw VTT bytes
    /// as one text/vtt sample, and Media3 1.10's TextRenderer ships
    /// with legacy sample decoding DISABLED — selecting such a track
    /// doesn't quietly show nothing, it throws ("Legacy decoding is
    /// disabled, can't handle text/vtt samples") and takes the whole
    /// playback down with it. SubtitleExtractor instead parses the VTT
    /// at load time into application/x-media3-cues samples, the only
    /// thing the renderer accepts.
    private fun buildMediaSourceFactory(dataSourceFactory: DataSource.Factory): MediaSource.Factory {
        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setPlaylistTrackerFactory { dsFactory, errorPolicy, parserFactory, cmcdConfig, executor ->
                DefaultHlsPlaylistTracker(dsFactory, errorPolicy, parserFactory, cmcdConfig, HLS_PLAYLIST_STUCK_COEFFICIENT, executor)
            }
        val subtitleParserFactory = DefaultSubtitleParserFactory()
        val progressiveFactory = DefaultMediaSourceFactory(dataSourceFactory)
        fun subtitleSource(config: MediaItem.SubtitleConfiguration): MediaSource {
            val format = Format.Builder()
                .setSampleMimeType(config.mimeType)
                .setLanguage(config.language)
                .setSelectionFlags(config.selectionFlags)
                .setRoleFlags(config.roleFlags)
                .setLabel(config.label)
                .setId(config.id)
                .build()
            // Unlike DefaultMediaSourceFactory we can't defer the fetch
            // until the track is selected (its lazy-single-track hook is
            // package-private), so each sideloaded VTT is fetched at
            // prepare() — they're small text files, an acceptable cost.
            val extractorsFactory = ExtractorsFactory {
                arrayOf(SubtitleExtractor(subtitleParserFactory.create(format), format))
            }
            return ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(MediaItem.fromUri(config.uri))
        }
        return object : MediaSource.Factory {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                if (mediaItem.localConfiguration?.mimeType != MimeTypes.APPLICATION_M3U8) {
                    return progressiveFactory.createMediaSource(mediaItem)
                }
                val content = hlsFactory.createMediaSource(mediaItem)
                val subtitleConfigs = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
                if (subtitleConfigs.isEmpty()) return content
                val subtitleSources = subtitleConfigs.map { subtitleSource(it) }
                return MergingMediaSource(content, *subtitleSources.toTypedArray())
            }

            override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_HLS, C.CONTENT_TYPE_OTHER)

            override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
                hlsFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                progressiveFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
                hlsFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                progressiveFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                return this
            }
        }
    }

    /// What the UI (PlayerView / its built-in controller) should hold.
    /// HLS sessions serve a growing EVENT playlist: a seek past what's
    /// been produced needs the hub to restart the pipeline elsewhere
    /// (§6 seek-anywhere, HUB-18), not a raw ExoPlayer seek — this
    /// reroutes the controller's seek/seekForward/seekBack through that
    /// logic transparently, mirroring web/src/views/Player.tsx:633-663.
    /// Unlike the web client we don't check whether the target is
    /// already inside the produced window first; every HLS seek
    /// round-trips through the hub. Simpler, always correct, and only
    /// costs a network round trip on a seek that could sometimes have
    /// been served locally — a fine trade for a first playback pass.
    val player: Player by lazy {
        object : ForwardingPlayer(realPlayer) {
            /// The seekbar now displays ABSOLUTE position/duration — the
            /// full original video length, with the current position
            /// marked at its true point (see getDuration()/
            /// getCurrentPosition()/getCurrentTimeline() below) — not
            /// "remaining time from 0", which put the scrubber at the
            /// wrong end of the bar and made resuming partway through
            /// look like starting over. PlayerControlView's drag-target
            /// arithmetic is entirely derived from what we report it, so
            /// a drag arrives here already in ABSOLUTE terms — no
            /// offsetMs arithmetic needed (unlike before this change,
            /// when the UI dealt in playlist-local terms).
            override fun seekTo(positionMs: Long) = handleSeek(positionMs)
            override fun seekForward() = handleSeek(offsetMs + realPlayer.currentPosition + realPlayer.seekForwardIncrement)
            override fun seekBack() = handleSeek((offsetMs + realPlayer.currentPosition - realPlayer.seekBackIncrement).coerceAtLeast(0))

            /// realPlayer's own duration only covers the HLS EVENT
            /// playlist's currently-produced window (it grows as the
            /// hub's pipeline keeps encoding) — reporting that straight
            /// to PlayerView's DefaultTimeBar is why the seekbar showed
            /// only a sliver instead of the whole video, with no way to
            /// drag to, say, the 30-minute mark before the hub has
            /// produced that far. The hub tells us the real total
            /// (session.durationMs) up front; reporting it directly here
            /// gives the seekbar its true, full length. Every seek
            /// round-trips through the hub regardless of target (see
            /// seekTo above), so dragging past what's currently produced
            /// is exactly as valid as dragging inside it.
            ///
            /// getDuration()/getContentDuration() alone (tried first)
            /// turned out not to be enough: disassembling
            /// PlayerControlView.class (Media3's source isn't vendored
            /// here) showed its time-bar update reads duration straight
            /// off Timeline.Window/Timeline.Period.durationUs — summed
            /// from getCurrentTimeline() — and only falls back to
            /// getContentDuration() when the timeline is completely
            /// empty. A real (short) HLS timeline always exists here, so
            /// that fallback path never runs; the fix has to happen in
            /// the Timeline itself, via getCurrentTimeline() below.
            override fun getDuration(): Long = contentDurationOrSuper()

            override fun getContentDuration(): Long = contentDurationOrSuper()

            private fun contentDurationOrSuper(): Long =
                session?.takeIf { it.mode != "direct" }?.durationMs ?: super.getDuration()

            /// Companions to getDuration() above: PlayerControlView reads
            /// getContentPosition()/getContentBufferedPosition() (a
            /// Player-level pair, not derived from the Timeline, unlike
            /// duration) straight into the time bar's scrubber and
            /// buffered-shading. Without these, position/buffered stay
            /// playlist-local (always starting near 0 for a fresh
            /// attach) while duration reports the full video — putting
            /// the scrubber at the wrong end of a bar that's otherwise
            /// the right length, and shading "buffered" from the video's
            /// start rather than from wherever playback actually is.
            /// offsetMs reconstructs the absolute position the same way
            /// reportProgressNow()/seekForward()/seekBack() already do;
            /// "direct" mode needs no reconstruction since it's a real
            /// byte-range file with true native seeking, already
            /// absolute.
            override fun getCurrentPosition(): Long = contentPositionOrSuper()

            override fun getContentPosition(): Long = contentPositionOrSuper()

            private fun contentPositionOrSuper(): Long =
                if (session?.mode == "direct") super.getCurrentPosition() else offsetMs + realPlayer.currentPosition

            override fun getBufferedPosition(): Long = contentBufferedPositionOrSuper()

            override fun getContentBufferedPosition(): Long = contentBufferedPositionOrSuper()

            private fun contentBufferedPositionOrSuper(): Long =
                if (session?.mode == "direct") super.getBufferedPosition() else offsetMs + realPlayer.bufferedPosition

            override fun isCurrentMediaItemSeekable(): Boolean =
                if (session?.mode != "direct") true else super.isCurrentMediaItemSeekable()

            /// ExoPlayer's own live detection (no #EXT-X-ENDLIST while
            /// the hub is still producing, see attach()'s
            /// LiveConfiguration note) would otherwise have PlayerView
            /// treat this as a live broadcast — a "LIVE" badge instead
            /// of a normal seekbar. It's a recording being produced on
            /// demand, not a broadcast — always report it as VOD.
            override fun isCurrentMediaItemLive(): Boolean = false

            /// The actual fix for the seekbar's length: PlayerControlView
            /// (and DefaultTimeBar underneath it) get the video's total
            /// duration by summing Timeline.Period.durationUs across the
            /// current Timeline.Window — never by calling a Player-level
            /// getter — so overriding getDuration()/getContentDuration()
            /// above was necessary but not sufficient. This wraps
            /// whatever (short, HLS-window-limited) Timeline realPlayer
            /// currently reports and rewrites the duration fields to the
            /// hub's real, absolute total, leaving everything else (ad
            /// state, period ids/uids, window count) untouched passthrough.
            override fun getCurrentTimeline(): Timeline {
                val real = super.getCurrentTimeline()
                if (real.isEmpty) return real
                val total = session?.takeIf { it.mode != "direct" }?.durationMs ?: return real
                return DurationOverrideTimeline(real, total * 1000L)
            }
        }
    }

    /// See getCurrentTimeline() above. Timeline has no public
    /// ForwardingTimeline helper in this Media3 version, but its surface
    /// is small (six abstract methods) — plain delegation for
    /// everything, with getWindow()/getPeriod() additionally overwriting
    /// the delegate-populated durationUs before returning it.
    private class DurationOverrideTimeline(
        private val delegate: Timeline,
        private val totalDurationUs: Long,
    ) : Timeline() {
        override fun getWindowCount(): Int = delegate.windowCount

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            delegate.getWindow(windowIndex, window, defaultPositionProjectionUs)
            window.durationUs = totalDurationUs
            window.isSeekable = true
            window.isDynamic = false
            return window
        }

        override fun getPeriodCount(): Int = delegate.periodCount

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            delegate.getPeriod(periodIndex, period, setIds)
            period.durationUs = totalDurationUs
            return period
        }

        override fun getIndexOfPeriod(uid: Any): Int = delegate.getIndexOfPeriod(uid)

        override fun getUidOfPeriod(periodIndex: Int): Any = delegate.getUidOfPeriod(periodIndex)
    }

    private var session: StartSessionResponse? = null
    private var offsetMs: Long = 0
    private var progressJob: Job? = null
    private var seekJob: Job? = null
    private var subtitleEpoch: Int = 0

    /// The offsetMs value baked into the current MediaItem's sideloaded
    /// VTT configs as `shift_ms` (see textDeliverySubtitleConfigs). When
    /// syncOrigin later corrects offsetMs, the baked shift is stale by
    /// the keyframe-snap delta — rebakeTextSubtitleShiftIfNeeded()
    /// compares against this to decide whether a re-prepare is owed.
    private var attachedVttShiftMs: Long = 0

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrack>> = _subtitleTracks

    /// null = "Off". Only Text-delivery selection changes ExoPlayer's own
    /// track override; Ass/Overlay delivery is read entirely by
    /// AssSubtitleOverlay/ImageSubtitleOverlay, driven off this + [subtitleSession].
    private val _selectedSubtitleTrack = MutableStateFlow<SubtitleTrack?>(null)
    val selectedSubtitleTrack: StateFlow<SubtitleTrack?> = _selectedSubtitleTrack

    private val _subtitleSession = MutableStateFlow<SubtitleSession?>(null)
    val subtitleSession: StateFlow<SubtitleSession?> = _subtitleSession

    init {
        realPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) reportProgressNow()
            }

            /// Diagnostic only: if the "start of video jumps forward"
            /// behavior recurs after the LiveConfiguration fix in
            /// attach(), this pins down whether ExoPlayer itself is
            /// issuing the jump (reason would be INTERNAL, not SEEK —
            /// SEEK is what our own handleSeek()/user drags produce) and
            /// what position it's landing on, rather than guessing at
            /// Media3 internals again.
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (kotlin.math.abs(newPosition.positionMs - oldPosition.positionMs) > 1000) {
                    Log.d(
                        TAG,
                        "position discontinuity reason=$reason old=${oldPosition.positionMs} new=${newPosition.positionMs}",
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // sessionId (not itemId) is what names the hub's scratch
                // dir (<data_dir>/sessions/<sessionId>/worker.log) — the
                // thing to check when this is a hub-side pipeline stall,
                // not a client bug.
                Log.e(TAG, "Playback failed for item=$itemId sessionId=${session?.sessionId}", error)
                progressJob?.cancel()
                _state.value = PlayerState.Error("Playback failed: ${error.readableMessage()}")
            }

            /// Diagnostic only: pins down whether a "seek far ahead ->
            /// black screen" report is ExoPlayer stuck BUFFERING forever
            /// (no onPlayerError ever fires, so PlayerState.Error never
            /// shows — matching a silent black screen) versus something
            /// that never even reaches the player (state stays whatever
            /// it was, no transition logged at all).
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(
                    TAG,
                    "playback state -> $playbackState (item=$itemId) " +
                        "currentPos=${realPlayer.currentPosition} bufferedPos=${realPlayer.bufferedPosition} " +
                        "playWhenReady=${realPlayer.playWhenReady}",
                )
            }

            /// Sideloaded subtitle track groups (attach()'s
            /// setSubtitleConfigurations) don't exist the instant
            /// prepare() returns — they show up here once metadata loads.
            /// Reapplying on every change is what carries a Text-delivery
            /// selection forward across a seek-restart's fresh MediaItem
            /// (a plain post-prepare() call would silently see no groups yet).
            override fun onTracksChanged(tracks: Tracks) {
                applySubtitleTrackSelectionOverride()
            }
        })
        start()
    }

    private fun start() {
        _state.value = PlayerState.Loading
        viewModelScope.launch {
            try {
                val profile = CapabilityProfileBuilder.build(getApplication())
                // Resolved BEFORE startSession, not alongside it: the
                // request below may only carry subtitle_track for a
                // BURN-delivery pick (see the burnPickOrNull note), and
                // delivery is only knowable from this list. Best-effort:
                // a failure here means no subtitle picker entries, not a
                // broken playback session, so it's swallowed rather than
                // failing the whole start().
                val tracks = try {
                    repo.subtitles(itemId, profile)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load subtitle tracks for item=$itemId", e)
                    emptyList()
                }
                _subtitleTracks.value = tracks
                // The Detail screen only carries the chosen track's id
                // through nav args; resolve it against the list once
                // it's loaded so the picker UI and text-delivery override
                // both see a real SubtitleTrack, not just its id.
                val initialTrack = tracks.firstOrNull { it.id == initialSubtitleTrackId }
                _selectedSubtitleTrack.value = initialTrack
                val session = repo.startSession(
                    itemId,
                    profile,
                    startMs,
                    audioTrack = initialAudioTrack,
                    subtitleTrack = burnPickOrNull(initialTrack),
                )
                this@PlayerViewModel.session = session
                Log.d(
                    TAG,
                    "session started item=$itemId requestedStartMs=$startMs mode=${session.mode} " +
                        "durationMs=${session.durationMs} partBaseMs=${session.partBaseMs}",
                )
                // offsetMs reconstructs the absolute video position from
                // ExoPlayer's own (playlist-local) currentPosition
                // elsewhere (reportProgressNow, the seek overrides,
                // subtitle timing) — it must be the ABSOLUTE position
                // this run's playlist starts at, i.e. startMs itself
                // (confirmed against the hub: execute_seek/start()
                // always hands the pipeline local_ms = abs_ms -
                // part.base_ms, and the produced playlist's own t=0 is
                // exactly that local_ms — so in absolute terms, t=0
                // always equals the FULL requested position, not just
                // the multi-part remainder). session.partBaseMs is a
                // different, mostly-irrelevant-here concept (multi-part
                // timeline stitching, 0 for virtually all content) that
                // this used to read into offsetMs instead — every prior
                // test happened to use startMs=0, where 0 and
                // partBaseMs(0) are indistinguishable, which is exactly
                // why this went unnoticed until a real seek target
                // exposed it. startPositionMs is correspondingly always
                // 0: this fresh playlist's own beginning IS where we
                // asked the pipeline to start, mirroring the web
                // client's unconditional `startPosition: 0` for hls.js.
                // syncOrigin() below refines offsetMs once the true
                // keyframe-snapped origin is known; it doesn't touch
                // startPositionMs, which never needed correcting.
                //
                // "direct" mode is the opposite arrangement: the file's
                // native timeline IS absolute, so offsetMs must stay 0
                // (offsetMs + currentPosition is how every absolute
                // position is reconstructed — progress reports, the
                // seek overrides, subtitle shift) and resuming partway
                // is a real player seek to startMs, not a pipeline
                // restart. The previous code applied the HLS recipe
                // (offsetMs = startMs, startPositionMs = 0) to direct
                // too, which both started resumed playback from the
                // beginning AND reported/rendered everything shifted by
                // startMs.
                if (session.mode == "direct") {
                    offsetMs = 0
                    attach(session, startPositionMs = startMs, requestedAbsMs = startMs)
                } else {
                    offsetMs = startMs
                    attach(session, startPositionMs = 0, requestedAbsMs = startMs)
                }
                _state.value = PlayerState.Ready
                startProgressLoop()
            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.readableMessage())
            }
        }
    }

    private fun handleSeek(targetMs: Long) {
        val session = session ?: return
        if (session.mode == "direct") {
            realPlayer.seekTo(targetMs)
            return
        }
        Log.d(TAG, "seek requested item=$itemId sessionId=${session.sessionId} targetMs=$targetMs currentOffsetMs=$offsetMs")
        // Seeks must not overlap: each one is pause -> hub round trip ->
        // offsetMs/attach, and two in flight at once (rapid double-taps,
        // repeated scrubber drags) race on whose response lands last —
        // the loser's attach()/offsetMs can describe a pipeline position
        // the hub isn't actually at anymore, desyncing subtitles and the
        // seekbar until the next seek. Cancelling the previous seek
        // aborts its in-flight HTTP call (suspend Retrofit), and joining
        // it before issuing ours keeps hub-side execution in the same
        // order as client-side attaches. CancellationException must
        // propagate untouched: a superseded seek is being replaced, so
        // its catch block must neither resume playback nor report an
        // error on the newer seek's behalf.
        val previousSeek = seekJob
        seekJob = viewModelScope.launch {
            previousSeek?.cancelAndJoin()
            try {
                realPlayer.pause()
                // No subtitle_track on seeks (mirrors the web client):
                // the hub's session already remembers an active burn
                // pick across seek-restarts, and its seek endpoint
                // treats any IMAGE-format track id as a NEW burn order —
                // sending the current selection here is what silently
                // converted an "overlay"-delivery PGS pick into a
                // burned-in encode on the first seek, leaving the video
                // with baked-in subtitles UNDER this client's own
                // overlay rendering of the same track (two copies,
                // independently timed — the "PGS out of sync" report),
                // with no way to switch off the burned copy afterwards.
                val result = repo.seek(session.sessionId, targetMs)
                // See start()'s comment: offsetMs is the ABSOLUTE
                // position this fresh playlist starts at (targetMs
                // itself), not result.partBaseMs — using partBaseMs here
                // (previously ~0 for single-part content) made
                // startPositionMs evaluate to targetMs verbatim, asking
                // ExoPlayer to seek to a LOCAL position far outside the
                // few-minutes-long fresh playlist's own timeline — it
                // would sit there forever waiting for local content that
                // was never going to arrive, which is exactly the "seek
                // far ahead hangs at BUFFERING" bug.
                offsetMs = targetMs
                Log.d(TAG, "seek accepted by hub item=$itemId newPartBaseMs=${result.partBaseMs}")
                attach(session, startPositionMs = 0, requestedAbsMs = targetMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The old stream is untouched (we only paused) — resume it
                // where it was and let the user retry the seek.
                Log.w(TAG, "seek failed item=$itemId targetMs=$targetMs", e)
                realPlayer.play()
                _transientError.value = "Seek failed: ${e.readableMessage()}"
            }
        }
    }

    /// A burn pick needs the hub to (re)negotiate the whole plan — the
    /// lightweight seek()-restart above assumes an EXISTING remux/
    /// transcode pipeline (`session.plan`), which a "direct"-mode
    /// session never has (direct is a raw byte passthrough; there's no
    /// pipeline to seek within). A session that started direct because
    /// no subtitle was picked yet can't be talked into burning one via
    /// seek() at all, so a burn pick/un-pick instead tears down and
    /// starts a brand new session at the current position — the same
    /// call start() makes, just resuming instead of from startMs.
    /// The only subtitle pick the hub may ever hear about: session start
    /// and seek both interpret an IMAGE-format track id as "burn this
    /// into the video" (crates/kahawai-hub/src/api.rs StartSessionRequest,
    /// sessions.rs seek()) — the hub cannot know this client renders
    /// "overlay" delivery itself. Text/ass picks are hub-side no-ops, so
    /// nothing but an explicit "burn"-delivery choice belongs on the wire.
    private fun burnPickOrNull(track: SubtitleTrack?): Long? =
        track?.takeIf { it.delivery == "burn" }?.id

    private fun restartSessionWithSubtitle(subtitleTrackId: Long?, revertTo: SubtitleTrack?) {
        viewModelScope.launch {
            try {
                realPlayer.pause()
                val positionMs = offsetMs + realPlayer.currentPosition
                val profile = CapabilityProfileBuilder.build(getApplication())
                val oldSessionId = session?.sessionId
                val newSession = repo.startSession(
                    itemId,
                    profile,
                    positionMs,
                    audioTrack = initialAudioTrack,
                    subtitleTrack = subtitleTrackId,
                )
                session = newSession
                // See start()'s comment on offsetMs/startPositionMs —
                // and un-picking a burn can legitimately come back as a
                // "direct" session, so both arrangements are reachable
                // here.
                if (newSession.mode == "direct") {
                    offsetMs = 0
                    attach(newSession, startPositionMs = positionMs, requestedAbsMs = positionMs)
                } else {
                    offsetMs = positionMs
                    attach(newSession, startPositionMs = 0, requestedAbsMs = positionMs)
                }
                if (oldSessionId != null && oldSessionId != newSession.sessionId) {
                    launch {
                        try {
                            repo.endSession(oldSessionId)
                        } catch (e: Exception) {
                            // Best-effort; the hub's own session reaper is the backstop.
                        }
                    }
                }
            } catch (e: Exception) {
                // The new session never started, so the old one is still
                // valid — resume it and roll the picker back to the
                // selection that's actually playing.
                _selectedSubtitleTrack.value = revertTo
                realPlayer.play()
                _transientError.value = "Failed to switch subtitle: ${e.readableMessage()}"
            }
        }
    }

    private fun buildMediaItem(session: StartSessionResponse): MediaItem {
        val uri = ApiClient.baseUrl().trimEnd('/') + session.streamUrl
        // Util.inferContentTypeForUriAndMimeType only recognizes HLS via an
        // exact match on the literal "application/x-mpegURL" — the hub's
        // session.contentType for remux/transcode is the more standard
        // "application/vnd.apple.mpegurl" (or similar), which silently
        // fails that match and sends DefaultMediaSourceFactory down the
        // generic container-extractor path against what's actually an
        // .m3u8 playlist (surfaces as "none of the extractors could read
        // the stream" — there's no HLS extractor in that list at all).
        // session.mode is the reliable signal already used in handleSeek:
        // "direct" is a real byte-range file (session.contentType is
        // accurate there), anything else is the HLS master playlist.
        val mimeType = if (session.mode == "direct") session.contentType else MimeTypes.APPLICATION_M3U8
        val subtitleConfigs = textDeliverySubtitleConfigs()
        Log.d(TAG, "attach item=$itemId textDeliverySubtitleCount=${subtitleConfigs.size} ids=${subtitleConfigs.map { it.id }}")
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setSubtitleConfigurations(subtitleConfigs)
        if (session.mode != "direct") {
            // The hub's EVENT playlist has no #EXT-X-ENDLIST until the
            // whole file has finished producing (kahawai-media's remux
            // pipeline only appends it at EOS), so ExoPlayer's HLS
            // implementation treats it as a live stream while it's still
            // growing — computing its own "live edge" target position
            // and nudging playback toward it, fighting the explicit
            // startPositionMs passed to setMediaItem below (this is what
            // made playback visibly skip forward a few seconds shortly
            // after starting, even from position 0). The web client hits
            // the exact same thing and disables it outright in hls.js
            // (liveSyncDurationCount/liveMaxLatencyDurationCount, see
            // Player.tsx's `attach` — "Watch from the beginning and never
            // chase").
            //
            // targetOffsetMs means "how far BEHIND the live edge should
            // playback sit" — left unset, Media3 computes its own small
            // heuristic default, which is what caused the original
            // "jumps to 9 seconds" bug. For a GROWING RECORDING, "the
            // start of the video" is the far end of that spectrum, so a
            // large-ish target forces the computed default position to
            // clamp down to the window's actual start instead of landing
            // a few seconds in.
            //
            // A previous version of this set targetOffsetMs/maxOffsetMs
            // to 24 HOURS — which fixed the jump, but (evidenced by a
            // seek-restart never reaching STATE_READY, hanging in
            // STATE_BUFFERING indefinitely, while a fresh start with the
            // SAME config reaches READY in ~200ms every time) also made
            // ExoPlayer treat 24 hours as a buffering target on a
            // seek-restart specifically — a physically impossible amount
            // to accumulate, so it never left BUFFERING. That, in turn,
            // meant it never played, so it never sent a progress report;
            // the hub's own post-seek throttle (kahawai-media's
            // install_pace_probe) gates further production on the
            // client's reported viewer position catching up to within a
            // window of what's already produced — so the client waiting
            // on the hub and the hub waiting on the client deadlocked
            // permanently. Three minutes is comfortably past what the
            // original few-seconds bug needed, and comfortably short of
            // "impossible to buffer" — long enough to survive the fix,
            // short enough not to recreate this deadlock.
            val liveOffsetMs = TimeUnit.MINUTES.toMillis(3)
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(1f)
                    .setMaxPlaybackSpeed(1f)
                    .setTargetOffsetMs(liveOffsetMs)
                    .setMinOffsetMs(0)
                    .setMaxOffsetMs(liveOffsetMs)
                    .build(),
            )
        }
        return mediaItemBuilder.build()
    }

    private fun attach(session: StartSessionResponse, startPositionMs: Long, requestedAbsMs: Long) {
        realPlayer.setMediaItem(buildMediaItem(session), startPositionMs)
        realPlayer.prepare()
        realPlayer.playWhenReady = true
        attachedVttShiftMs = offsetMs
        // Track groups for the just-set MediaItem don't exist yet (they
        // load asynchronously) — the onTracksChanged listener in init{}
        // reapplies the pinned selection once they do. Nothing to do here
        // beyond that; a stale override from the PREVIOUS MediaItem can't
        // linger since setMediaItem/prepare drops it.
        // The stream URL's last path segment is the playlist/manifest
        // file (master.m3u8 for remux/transcode); the session's
        // out-of-band taps (subs-{id}.ass / subs-{id}.jsonl) live
        // alongside it. epoch bumps on every attach() (initial + every
        // seek-restart) so AssSubtitleOverlay/ImageSubtitleOverlay know
        // to reconnect even though streamBaseUrl itself doesn't change
        // within one session.
        subtitleEpoch++
        val uri = ApiClient.baseUrl().trimEnd('/') + session.streamUrl
        val streamBaseUrl = uri.substringBeforeLast('/') + "/"
        _subtitleSession.value = SubtitleSession(
            streamBaseUrl = streamBaseUrl,
            isHls = session.mode != "direct",
            epoch = subtitleEpoch,
        )
        // offsetMs (partBaseMs) is only correct for multi-part timeline
        // stitching — it says nothing about where the pipeline actually
        // landed within ITS part, which is 0 only when the requested
        // position happens to fall exactly on a keyframe. Every other
        // case snaps backward to the nearest keyframe at-or-before it,
        // and the true origin is knowable only once the pipeline reports
        // it. "direct" mode is a raw byte-range file with real seeking —
        // no pipeline-local timeline to correct. Starting from the true
        // beginning (0) is skipped too: 0 is always a keyframe, so
        // there's nothing to correct and no reason to pay the round trip.
        if (session.mode != "direct" && requestedAbsMs != 0L) {
            syncOrigin(session, streamBaseUrl, subtitleEpoch)
        }
    }

    /// Corrects the optimistic [offsetMs] once the hub reports the
    /// pipeline's true (keyframe-snapped) origin via the session's
    /// `start.pos` sidecar — mirrors web/src/views/Player.tsx's
    /// syncOrigin ("players align subtitles/seekbar to it", per the
    /// hub's own session_file comment). The mismatch this corrects is
    /// exactly what shows up as subtitles and the reported/seekbar
    /// position drifting out of sync with the video specifically when
    /// resuming or seeking into the middle of something — never when
    /// starting from the beginning.
    ///
    /// The correction is a plain in-place [offsetMs] write (no epoch
    /// bump, no [SubtitleSession] change): ass/overlay rendering and the
    /// seekbar both read position through the ForwardingPlayer, whose
    /// overrides fold in offsetMs at call time, so they pick it up on
    /// their very next read. Text-delivery VTT is the one consumer that
    /// CAN'T — its shift_ms is baked into a sideloaded
    /// MediaItem.SubtitleConfiguration at prepare() time — so it's
    /// handled by rebakeTextSubtitleShiftIfNeeded(): a position-
    /// preserving re-prepare, paid only when a text track is actually
    /// selected (at correction time, or later at pick time via
    /// selectSubtitleTrack).
    ///
    /// [start.pos] may not exist yet the instant a fresh pipeline starts,
    /// so this retries a few times before giving up — same shape as the
    /// web client's 3-attempt/700ms backoff. [forEpoch] guards against a
    /// stale attempt (a slow retry from a PREVIOUS attach()) landing
    /// after a newer seek/restart has already superseded it.
    private fun syncOrigin(session: StartSessionResponse, streamBaseUrl: String, forEpoch: Int) {
        val optimisticOffsetMs = offsetMs
        viewModelScope.launch {
            repeat(3) { attempt ->
                if (subtitleEpoch != forEpoch) {
                    Log.d(TAG, "syncOrigin abandoned epoch=$forEpoch (superseded, now ${subtitleEpoch}) attempt=$attempt")
                    return@launch
                }
                try {
                    // Both execute() AND reading the response body are
                    // blocking calls — viewModelScope.launch{} defaults to
                    // Dispatchers.Main.immediate, so without this the very
                    // first one trips StrictMode's
                    // NetworkOnMainThreadException on every single attempt.
                    // That exception was being swallowed by the catch
                    // below (logged, then retried, 3 times, always the
                    // same failure) — meaning this correction has never
                    // actually landed once, since it was first written.
                    // AssSubtitleOverlay's own blocking OkHttp calls
                    // already do this correctly; this one just didn't.
                    // Everything that touches the response has to stay
                    // inside the IO-confined block too, not just execute()
                    // itself.
                    val local = withContext(Dispatchers.IO) {
                        ApiClient.authenticatedOkHttpClient()
                            .newCall(Request.Builder().url("${streamBaseUrl}start.pos").build())
                            .execute()
                            .use { response ->
                                if (!response.isSuccessful) {
                                    Log.d(TAG, "syncOrigin http ${response.code} epoch=$forEpoch attempt=$attempt")
                                    return@use null
                                }
                                val raw = response.body?.string()?.trim()
                                val parsed = raw?.toDoubleOrNull()?.let(Math::round)
                                if (parsed == null) Log.d(TAG, "syncOrigin unparseable epoch=$forEpoch attempt=$attempt raw=$raw")
                                parsed
                            }
                    }
                    if (local != null) {
                        val corrected = (session.partBaseMs ?: 0) + local
                        val inBounds = kotlin.math.abs(corrected - offsetMs) < 60_000
                        Log.d(
                            TAG,
                            "syncOrigin epoch=$forEpoch attempt=$attempt local=$local " +
                                "partBaseMs=${session.partBaseMs} optimisticOffsetMs=$optimisticOffsetMs " +
                                "corrected=$corrected currentOffsetMs=$offsetMs inBounds=$inBounds",
                        )
                        // Sanity-bounded like the web client: a wildly
                        // different value means we're reading the wrong
                        // session/a stale file, not a real keyframe snap.
                        if (subtitleEpoch == forEpoch && corrected != offsetMs && inBounds) {
                            offsetMs = corrected
                            Log.d(TAG, "syncOrigin applied epoch=$forEpoch offsetMs=$corrected")
                            if (_selectedSubtitleTrack.value?.delivery == "text") {
                                rebakeTextSubtitleShiftIfNeeded()
                            }
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "syncOrigin fetch failed epoch=$forEpoch attempt=$attempt", e)
                    // Not ready yet, or a transient network hiccup — retry.
                }
                delay(700)
            }
        }
    }

    /// Media3 offers no way to adjust a sideloaded subtitle's time shift
    /// on a live MediaItem, so when [offsetMs] has moved past the value
    /// baked into the current configs (syncOrigin's keyframe-snap
    /// correction — typically a few seconds after any seek that didn't
    /// land exactly on a keyframe), the only fix is re-preparing the same
    /// stream with freshly-baked configs. That's a visible rebuffer, so
    /// it's deliberately NOT done on every correction — only when a text
    /// track is actually selected (callers gate on that), where the
    /// alternative is subtitles permanently off by the snap delta.
    /// Position is preserved: realPlayer.currentPosition is playlist-
    /// local and the playlist itself is unchanged, so handing it back as
    /// startPositionMs resumes in place.
    private fun rebakeTextSubtitleShiftIfNeeded() {
        val session = session ?: return
        if (session.mode == "direct") return // shift is always 0 for direct
        if (attachedVttShiftMs == offsetMs) return
        Log.d(TAG, "rebaking VTT shift item=$itemId stale=$attachedVttShiftMs fresh=$offsetMs")
        val resumeLocalMs = realPlayer.currentPosition
        val wasPlaying = realPlayer.playWhenReady
        realPlayer.setMediaItem(buildMediaItem(session), resumeLocalMs)
        realPlayer.prepare()
        realPlayer.playWhenReady = wasPlaying
        attachedVttShiftMs = offsetMs
    }

    /// Media3 can't attach a new [MediaItem.SubtitleConfiguration] to an
    /// already-prepared MediaItem, so every Text-delivery track is
    /// sideloaded here upfront; switching between them is then a
    /// TrackSelectionOverride (applySubtitleTrackSelectionOverride) with
    /// no re-prepare. `shift_ms` is recomputed every attach() call since
    /// offsetMs changes on every seek-restart. For "direct" sessions
    /// offsetMs is pinned to 0 (the native timeline is already absolute),
    /// so the shift correctly evaluates to 0 there.
    private fun textDeliverySubtitleConfigs(): List<MediaItem.SubtitleConfiguration> =
        _subtitleTracks.value
            .filter { it.delivery == "text" }
            .map { track ->
                val vttUrl = "${ApiClient.baseUrl().trimEnd('/')}/api/v1/items/$itemId/subtitles/${track.id}.vtt?shift_ms=${-offsetMs}"
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(vttUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(track.language)
                    .setId(track.id.toString())
                    .build()
            }

    /// Reapplies the current [selectedSubtitleTrack] as a
    /// TrackSelectionOverride against whatever sideloaded text tracks the
    /// just-rebuilt MediaItem carries. A no-op (override cleared) unless
    /// the selection is itself Text delivery — Ass/Overlay tracks are
    /// never part of ExoPlayer's own track groups.
    private fun applySubtitleTrackSelectionOverride() {
        val track = _selectedSubtitleTrack.value
        val params = realPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (track != null && track.delivery == "text") {
            // MergingMediaPeriod re-exposes every child source's formats
            // with the id rewritten to "<childIndex>:<originalId>"
            // (uniqueness across children — confirmed in 1.10.0
            // bytecode), so the sideloaded VTT config's id "1234"
            // surfaces here as e.g. "1:1234" and an exact comparison
            // never matched — the override was silently skipped and
            // text tracks never turned on. The hub's HLS playlists carry
            // no subtitle renditions of their own, so every TEXT group
            // is one of ours and suffix matching is unambiguous.
            val wantedId = track.id.toString()
            val group = realPlayer.currentTracks.groups.firstOrNull {
                it.type == C.TRACK_TYPE_TEXT &&
                    it.mediaTrackGroup.getFormat(0).id?.substringAfterLast(':') == wantedId
            }
            // Log.i, not Log.d: the vivo test device suppresses D-level
            // logcat output entirely, and this is the one line that says
            // whether a text pick actually engaged.
            Log.i(
                TAG,
                "text override: want=$wantedId matched=${group != null} " +
                    "textGroups=${realPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.map { it.mediaTrackGroup.getFormat(0).id }}",
            )
            if (group != null) params.addOverride(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        realPlayer.trackSelectionParameters = params.build()
    }

    /// null clears selection ("Off"). Text-delivery switches are instant
    /// (TrackSelectionOverride over already-sideloaded VTT configs, no
    /// re-prepare); Ass/Overlay delivery is picked up by
    /// AssSubtitleOverlay/ImageSubtitleOverlay observing [selectedSubtitleTrack]
    /// + [subtitleSession] — neither needs a session restart, matching the
    /// hub's own rule that a text/ass/downloaded pick has no plan impact
    /// (the client fetches those itself).
    ///
    /// A "burn" pick is different: the hub bakes it into the encoded
    /// video server-side, and — now that the Detail screen can
    /// pre-select a subtitle before Play — a session can arrive here
    /// already burning one. Undoing or changing that is a plan change
    /// only the hub can make, so a burn pick on either end of the
    /// switch restarts the session (see [restartSessionWithSubtitle])
    /// instead of the client-only override below.
    fun selectSubtitleTrack(track: SubtitleTrack?) {
        val previous = _selectedSubtitleTrack.value
        _selectedSubtitleTrack.value = track
        if (previous?.delivery == "burn" || track?.delivery == "burn") {
            // burnPickOrNull, not track?.id: switching AWAY from a burn
            // to an overlay/text track must start the new session with
            // NO pick — passing an overlay track's id here would just
            // order a fresh burn of the new track instead of un-burning.
            restartSessionWithSubtitle(burnPickOrNull(track), revertTo = previous)
        } else {
            // Text delivery lives on tracks attached at the START of the
            // CURRENT MediaItem (realPlayer.currentTracks), so this doesn't
            // need a fresh attach() — unlike the initial sideload list
            // itself. One exception: if syncOrigin corrected offsetMs
            // AFTER those tracks were baked, a text pick made now would
            // render off by the snap delta — the rebake (a no-op when the
            // baked shift is still current) settles that first; its
            // re-prepare path applies the override via onTracksChanged.
            if (track?.delivery == "text") rebakeTextSubtitleShiftIfNeeded()
            applySubtitleTrackSelectionOverride()
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                // Diagnostic: onPlaybackStateChanged only logs on
                // transitions, so it goes silent for the whole
                // duration of a stuck BUFFERING period. This fires
                // every tick regardless, to show whether
                // currentPosition/bufferedPosition are moving at all
                // while stuck.
                Log.d(
                    TAG,
                    "progress tick item=$itemId state=${realPlayer.playbackState} " +
                        "currentPos=${realPlayer.currentPosition} bufferedPos=${realPlayer.bufferedPosition} " +
                        "playWhenReady=${realPlayer.playWhenReady}",
                )
                // playWhenReady, not isPlaying — mirrors the web client's
                // gate (web/src/views/Player.tsx's tick: `if
                // (!video.paused) report()`). HTML5's `.paused` only
                // reflects an explicit pause() call, staying false
                // through a stall/rebuffer; ExoPlayer's isPlaying is
                // stricter — false during BUFFERING too, not just
                // PAUSED. Gating on isPlaying meant a seek-restart stuck
                // in BUFFERING never reported progress at all, which
                // silently starved the hub's own post-seek throttle
                // (kahawai-media's install_pace_probe gates further
                // production on viewer.pos catching up to what's already
                // produced) — client and hub waiting on each other,
                // permanently. playWhenReady stays true straight through
                // buffering, exactly like `!paused`, and only goes false
                // on the explicit pause() handleSeek() already does
                // before restarting.
                if (realPlayer.playWhenReady) reportProgressNow()
            }
        }
    }

    private fun reportProgressNow() {
        val session = session ?: return
        val positionMs = offsetMs + realPlayer.currentPosition
        viewModelScope.launch {
            try {
                repo.reportProgress(session.sessionId, positionMs)
                Log.d(TAG, "progress reported sessionId=${session.sessionId} positionMs=$positionMs")
            } catch (e: Exception) {
                Log.w(TAG, "progress report failed sessionId=${session.sessionId} positionMs=$positionMs", e)
                // Best-effort, like the web client's beforeunload report.
            }
        }
    }

    /// The ViewModel's own scope is cancelled around the same time
    /// onCleared() runs, which isn't a reliable place to await a network
    /// call — this cleanup is best-effort background work, same as the
    /// web client's `beforeunload` handler (web/src/views/Player.tsx:695-699).
    override fun onCleared() {
        val session = session
        val finalPositionMs = if (session != null) offsetMs + realPlayer.currentPosition else null
        realPlayer.release()
        if (session != null && finalPositionMs != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    repo.reportProgress(session.sessionId, finalPositionMs)
                    repo.endSession(session.sessionId)
                } catch (e: Exception) {
                    // Nothing left to recover to; the hub's own session
                    // reaper is the backstop.
                }
            }
        }
        super.onCleared()
    }
}
