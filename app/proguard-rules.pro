# R8 rules for the release build.
# Release is minified for speed on Pepper's tablet (Android 6): unminified Compose keeps its
# debug/trace calls and gets no inlining. Library rules (Compose, Hilt, OkHttp, Coil, Gson core)
# ship with the libraries; the rules below cover reflection and JNI they cannot know about.

# Keep line numbers so crash stack traces from the robot stay readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations, generics and inner classes needed by Gson and QiSDK
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# --- App code ---------------------------------------------------------------

# Gson event models: fields are populated by reflection (RealtimeEventHandler)
-keep class ch.fhnw.pepper_realtime.network.RealtimeEvents { *; }
-keep class ch.fhnw.pepper_realtime.network.RealtimeEvents$* { *; }
-keep class ch.fhnw.pepper_realtime.network.GoogleLiveEvents { *; }
-keep class ch.fhnw.pepper_realtime.network.GoogleLiveEvents$* { *; }

# Data classes: MovementController reads SavedLocation.translation by reflection, and
# persisted rules/settings are restored with enum valueOf()
-keep class ch.fhnw.pepper_realtime.data.** { *; }
-keepclassmembers enum ch.fhnw.pepper_realtime.** { *; }

# --- Gson -------------------------------------------------------------------
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- QiSDK / libqi (Pepper): JNI and reflection-based proxies of NAOqi objects ---
-keep class com.aldebaran.** { *; }
-keep interface com.aldebaran.** { *; }
-keep class com.softbankrobotics.** { *; }
-keep interface com.softbankrobotics.** { *; }
-dontwarn com.aldebaran.**
-dontwarn com.softbankrobotics.**

# --- Azure Speech SDK (JNI) -------------------------------------------------
-keep class com.microsoft.cognitiveservices.** { *; }
-dontwarn com.microsoft.cognitiveservices.**

# --- sshj + BouncyCastle (crypto algorithms looked up by name) --------------
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.slf4j.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# --- Strip verbose logging from release (Log.i/w/e are kept for diagnostics) ---
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
