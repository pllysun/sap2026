# ===========================================================
#  软协课表 R8 / ProGuard 规则（release 混淆 + 资源压缩）
# ===========================================================
# 项目无 @SerializedName，Gson 全靠「字段名」当 JSON key，
# 故所有会被 Gson 反序列化的本地模型必须整类保留成员名(*;)，
# 否则混淆后字段名改写 → 旧 SharedPreferences 缓存反序列化丢数据。

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# ---- 后端 API DTO（Retrofit/Gson 反射）----
-keep class edu.csuft.sap.data.remote.dto.** { *; }

# ---- 本地持久化模型（Gson 反射，散落在多个包）----
# ScheduleStore / WidgetRepository 反射 ScheduleRoot::class
-keep class edu.csuft.sap.data.schedule.** { *; }
# JwCacheStore 反射 JwCacheRoot::class（成绩/考试/学期/评教缓存）
-keep class edu.csuft.sap.data.local.** { *; }
# AccountManager 反射 BoundAccount / 备注名 Map<String,String>
-keep class edu.csuft.sap.data.account.** { *; }
# 桌面小组件的数据类（与 ScheduleRoot 同包，顺手保稳）
-keep class edu.csuft.sap.widget.WidgetCourse { *; }
-keep class edu.csuft.sap.widget.WidgetData { *; }

# ---- Gson 通用：注解字段 + 枚举(ProfileKind 等)序列化 ----
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- security-crypto / Tink（EncryptedSharedPreferences 存 token）----
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn javax.annotation.**

# ---- 网络栈（自带 consumer rules，补保险）----
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**

# Compose / Material3 / Navigation / AGP 自带 consumer proguard rules，无需手写 keep。
