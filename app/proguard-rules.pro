# Hermes Chat — Proguard rules.
# minifyEnabled=false saat ini, file ini disiapkan untuk build release mendatang.
-keep class id.sukiapps.hermes.data.local.** { *; }
-keep class id.sukiapps.hermes.data.remote.** { *; }
-keepclasseswithmembernames class * {
    @androidx.room.* <methods>;
}
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
