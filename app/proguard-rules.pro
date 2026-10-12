# Room 生成的实现类通过反射加载
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# SQLCipher 通过 JNI 加载原生库
-keep class net.zetetic.database.** { *; }
-keepclassmembers class net.zetetic.database.** { *; }

# kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    public static ** serializer(...);
}

# Glance 小组件通过反射实例化 Receiver 与 ActionCallback
-keep class androidx.glance.** { *; }

# Hilt 生成的组件
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper
