-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class at.creepervm1000.mobileclaw.**$$serializer { *; }
-keepclassmembers class at.creepervm1000.mobileclaw.** {
    *** Companion;
}
-keepclasseswithmembers class at.creepervm1000.mobileclaw.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Persisted by name in DataStore (Prefs.kt does Provider.valueOf(name)),
# so the enum constants must keep their original names across obfuscation.
-keepclassmembers enum at.creepervm1000.mobileclaw.llm.Provider { *; }

# Shizuku's ShizukuProvider is registered in the manifest and Shizuku.newProcess is
# reached reflectively (see ShizukuTools.kt), so keep the whole client API intact.
-keep class rikka.shizuku.** { *; }
