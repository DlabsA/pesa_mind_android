# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Gson (via Retrofit's GsonConverterFactory) serializes/deserializes API DTOs by reflection
# using each field's runtime name, falling back to @SerializedName only if that annotation
# attribute survives R8. Without these rules R8 both renames fields (a, b, c...) AND strips
# annotations, so a minified release build silently sends/reads wrong JSON keys — request
# DTOs that worked in an unminified debug build fail on the backend with 400s.
-keepattributes Signature
-keepattributes *Annotation*
# core.network.models.** and core.network.analytics.** are known DTO packages today, but a
# package-by-package list is exactly how this broke the first time (DashboardResponse lived
# in .analytics, not .models, and got obfuscated even after the first fix). Keep the whole
# network tree, plus a package-agnostic safety net below for @SerializedName fields anywhere
# else in the app (e.g. billing, database sync DTOs) that this doesn't already cover.
-keep class cc.dlabs.pesamind.core.network.** {
    <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer