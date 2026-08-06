# kotlinx-serialization, Retrofit, and the libass wrapper (ass/ass-kt/ass-media)
# each ship their own consumer proguard rules bundled in their AARs/jars
# (META-INF/proguard), which AGP applies automatically — no manual keep
# rules needed for DTOs or the JNI-facing ass-kt classes.

# R8 prunes any DTO class whose API-call result is ignored at every call
# site (bootstrap()'s reachability probe, putPref()'s fire-and-forget
# ack): the class is deleted, its mention in the suspend method's
# Continuation generic signature is rewritten to java.lang.Object, and
# the call then dies at runtime with "Unable to create converter for
# class java.lang.Object". Keep the DTO package resident — names may
# still be obfuscated; R8 rewrites signatures to match renamed classes.
-keep,allowobfuscation class com.kolktech.kahawai.data.network.dto.** { *; }
