# ═══════════════════════════════════════════════════════════
#  Base
# ═══════════════════════════════════════════════════════════
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-dontobfuscate

# ═══════════════════════════════════════════════════════════
#  Kotlin Serialization
# ═══════════════════════════════════════════════════════════
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.translator.app.data.settings.**$$serializer { *; }
-keepclassmembers class com.translator.app.data.settings.** { *** Companion; }
-keepclasseswithmembers class com.translator.app.data.settings.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class *
-keepclassmembers class * { @kotlinx.serialization.SerialName *; }

# ═══════════════════════════════════════════════════════════
#  Kotlin reflection / metadata
# ═══════════════════════════════════════════════════════════
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }

# ═══════════════════════════════════════════════════════════
#  Coroutines
# ═══════════════════════════════════════════════════════════
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlinx.coroutines.flow.**
-keepnames class kotlinx.coroutines.flow.** { *; }
-keepclassmembers class kotlinx.coroutines.channels.** { *; }

# ═══════════════════════════════════════════════════════════
#  Android Keystore
# ═══════════════════════════════════════════════════════════
-keep class android.security.keystore.** { *; }

# ═══════════════════════════════════════════════════════════
#  OkHttp 5.x / Okio
# ═══════════════════════════════════════════════════════════
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ═══════════════════════════════════════════════════════════
#  Hilt / Dagger
# ═══════════════════════════════════════════════════════════
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { <init>(...); }
-keepclassmembers class * { @javax.inject.Inject <init>(...); }

# ═══════════════════════════════════════════════════════════
#  Jetpack Compose
# ═══════════════════════════════════════════════════════════
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keepclassmembers class androidx.compose.** { <init>(...); }

# ═══════════════════════════════════════════════════════════
#  Timber
# ═══════════════════════════════════════════════════════════
-dontwarn org.jetbrains.annotations.**