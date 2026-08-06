-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class dev.cmobile.agent.**$$serializer { *; }
-keepclassmembers class dev.cmobile.agent.** {
    *** Companion;
}
-keep class rikka.shizuku.** { *; }
