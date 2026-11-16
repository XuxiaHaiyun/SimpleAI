# ===== Gson =====
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.** { *; }
-keep class com.google.gson.stream.** { *; }
# 保留泛型签名（Gson 解析需要）
-keepattributes Signature
-keepattributes *Annotation*
# 保留被 Gson 序列化的数据模型
-keep class com.xxhy.simpleai.data.model.** { *; }
-keep class com.xxhy.simpleai.network.model.** { *; }
# Gson TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ===== OkHttp 3.x =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ===== Markwon =====
-keep class io.noties.markwon.** { *; }
-keep class io.noties.prism4j.** { *; }
-dontwarn com.caverock.androidsvg.**
-dontwarn pl.droidsonroids.gif.**

# ===== AndroidX / Material =====
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ===== 本应用入口 =====
-keep class com.xxhy.simpleai.App { *; }
-keep class com.xxhy.simpleai.MainActivity { *; }
-keep class com.xxhy.simpleai.ui.** { *; }
-keep class com.xxhy.simpleai.data.** { *; }
-keep class com.xxhy.simpleai.network.** { *; }

# JLatexMath
-keep class ru.noties.jlatexmath.** { *; }

# 保留 R 资源引用
-keepclassmembers class **.R$* {
    public static <fields>;
}
