# kotlinx-serialization, Retrofit, and the libass wrapper (ass/ass-kt/ass-media)
# each ship their own consumer proguard rules bundled in their AARs/jars
# (META-INF/proguard), which AGP applies automatically — no manual keep
# rules needed for DTOs, Retrofit interfaces, or the JNI-facing ass-kt
# classes.
