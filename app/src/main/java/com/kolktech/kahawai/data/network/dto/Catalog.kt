package com.kolktech.kahawai.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class LibrarySummary(
    val id: String,
    val name: String,
    val mediaType: String,
)

@Serializable
data class LibrariesResponse(val libraries: List<LibrarySummary>)

/// Mirrors `Item` in web/src/api.ts:103-137 — the browse/search/children
/// row shape. `kind` is one of movie|show|episode|album|track.
@Serializable
data class Item(
    val id: String,
    val kind: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val parentId: String? = null,
    val parentTitle: String? = null,
    val sources: Int = 0,
    val artVersion: Long? = null,
    val premiered: String? = null,
    val resumePositionMs: Long? = null,
    val resumeDurationMs: Long? = null,
    val played: Boolean = false,
    val playCount: Int = 0,
)

@Serializable
data class ItemsResponse(
    val items: List<Item>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class ChildrenResponse(val children: List<Item>)

@Serializable
data class ItemMetadata(
    val overview: String? = null,
    val rating: Double? = null,
    val premiered: String? = null,
    val genres: List<String>? = null,
)

/// crates/kahawai-hub/src/api.rs:2199-2233 — one playable source file
/// backing an item.
@Serializable
data class Source(
    val moduleId: String,
    val collectionId: String,
    val pathRel: String,
    val size: Long,
    val available: Boolean,
    val revision: Int,
)

/// The detail endpoint (crates/kahawai-hub/src/api.rs:2171-2311)
/// overwrites the list endpoint's numeric `sources` count with the full
/// array of source objects (api.rs:2239) — deliberately not the same
/// shape as [Item.sources].
@Serializable
data class ItemDetail(
    val id: String,
    val kind: String,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val parentId: String? = null,
    val parentTitle: String? = null,
    val showTitle: String? = null,
    val sources: List<Source> = emptyList(),
    val artVersion: Long? = null,
    val premiered: String? = null,
    val resumePositionMs: Long? = null,
    val resumeDurationMs: Long? = null,
    val played: Boolean = false,
    val playCount: Int = 0,
    val metadata: ItemMetadata? = null,
)
