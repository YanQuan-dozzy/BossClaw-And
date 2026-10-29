# BossClaw Android release rules
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class com.bossclaw.android.llm.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
