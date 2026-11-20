# Project ProGuard / R8 keep rules for Mumbai Share Auto
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class com.mumbaishareauto.app.MainActivity { *; }
-keep class com.mumbaishareauto.app.BookingDomainKt { *; }
-keep class com.mumbaishareauto.app.DriverWorkflowDomainKt { *; }
-keep class com.mumbaishareauto.app.PassengerWorkflowDomainKt { *; }
-keep class com.mumbaishareauto.app.BuildConfig { *; }
-keep class com.mumbaishareauto.app.model.** { *; }
-keep class com.mumbaishareauto.app.data.remote.** { *; }
-keep class com.mumbaishareauto.app.data.local.db.** { *; }
-keep class com.mumbaishareauto.app.auth.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-dontwarn org.jetbrains.annotations.**
