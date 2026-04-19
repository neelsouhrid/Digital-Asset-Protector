# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools proguard-android.txt

# Keep Web3j classes
-keep class org.web3j.** { *; }
-dontwarn org.web3j.**

# Keep Room entities
-keep class com.assetvault.data.** { *; }

# Keep Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Keep Gson
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-dontwarn com.google.gson.**