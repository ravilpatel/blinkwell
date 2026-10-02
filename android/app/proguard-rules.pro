# BlinkWell Proguard Rules

# Keep ML Kit components
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep Supabase & Ktor serialization classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Data Models
-keep class com.mitalipurohit.blinkwell.data.local.entity.** { *; }
-keep class com.mitalipurohit.blinkwell.data.remote.model.** { *; }
