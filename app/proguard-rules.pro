# Keep Gson / Retrofit DTOs for release builds
-keep class com.forgeai.chat.data.remote.dto.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
