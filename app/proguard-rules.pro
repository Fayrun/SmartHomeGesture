# SmartHomeGesture ProGuard rules

# Keep data model classes (used with Gson serialization)
-keep class com.example.smarthomegesture.SmartDevice { *; }
-keep class com.example.smarthomegesture.DeviceCommand { *; }

# Keep enums
-keepclassmembers enum com.example.smarthomegesture.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
