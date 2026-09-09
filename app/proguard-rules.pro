# ---- Kotlin ----
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# ---- OkHttp / Okio ----
# OkHttp references optional TLS providers that we don't ship.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- Retrofit ----
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Keep Retrofit service interfaces (they're used reflectively)
-keep interface io.github.playfoundryhq.adaptiveflow.data.ai.**$Service { *; }

# ---- Moshi ----
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
  @com.squareup.moshi.Json <fields>;
  @com.squareup.moshi.FromJson <methods>;
  @com.squareup.moshi.ToJson <methods>;
}
-keep class **JsonAdapter { <init>(...); *; }
-keepnames @com.squareup.moshi.JsonClass class *
# Our JSON model types (Moshi + KotlinJsonAdapterFactory reflection)
-keep class io.github.playfoundryhq.adaptiveflow.ui.viewmodel.ParsedCard { *; }
-keep class io.github.playfoundryhq.adaptiveflow.ui.viewmodel.ParsedDeck { *; }
-keep class io.github.playfoundryhq.adaptiveflow.data.ai.**$* { *; }

# ---- Jetpack Security / Tink (EncryptedSharedPreferences) ----
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.crypto.tink.** { *; }
# Tink's optional KeysDownloader pulls google-http-client / joda-time which we
# never use (we only do local AEAD).
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ---- PdfBox-Android ----
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn org.apache.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# ---- App entities (Room reflection at build time is fine; keep for safety) ----
-keep class io.github.playfoundryhq.adaptiveflow.data.model.** { *; }
