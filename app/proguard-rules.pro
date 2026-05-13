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
# 混合时不使用大小写混合，混合后的类名为小写
-dontusemixedcaseclassnames

# 这句话能够使我们的项目混淆后产生映射文件
# 包含有类名->混淆后类名的映射关系
-verbose

# 保留Annotation不混淆
-keepattributes *Annotation*,InnerClasses

# 避免混淆泛型
-keepattributes Signature

# 指定混淆是采用的算法，后面的参数是一个过滤器
# 这个过滤器是谷歌推荐的算法，一般不做更改
-optimizations !code/simplification/cast,!field/*,!class/merging/*

-flattenpackagehierarchy

#############################################
#
# Android开发中一些需要保留的公共部分
#
#############################################
# 屏蔽错误Unresolved class name
#noinspection ShrinkerUnresolvedReference

# 移除Log类打印各个等级日志的代码，打正式包的时候可以做为禁log使用，这里可以作为禁止log打印的功能使用
# 记得proguard-android.txt中一定不要加-dontoptimize才起作用
# 另外的一种实现方案是通过BuildConfig.DEBUG的变量来控制
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# 保持js引擎调用的java类
-keep class * extends io.legado.app.help.JsExtensions{*;}
# 数据类
-keep class **.data.entities.**{*;}
# Gson 保留字段信息，防止混淆后字段名改变导致反序列化失败
-keepattributes Signature
-keepattributes *Annotation*
# 保留 data class 的 companion object 中的 jsonDeserializer
-keepclassmembers class **.data.entities.rule.** {
    public static com.google.gson.JsonDeserializer jsonDeserializer;
}
# 保留所有 Rule 类的 companion object
-keepclassmembers class **.data.entities.rule.** {
    public static ** Companion;
    public static ** jsonDeserializer;
}
# 保留 Gson 需要的字段名
-keepclassmembernames class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# 保留 Rule 类的类名，防止 Gson TypeAdapter 注册失效
-keepnames class **.data.entities.rule.ExploreRule
-keepnames class **.data.entities.rule.SearchRule
-keepnames class **.data.entities.rule.BookInfoRule
-keepnames class **.data.entities.rule.TocRule
-keepnames class **.data.entities.rule.ContentRule
-keepnames class **.data.entities.rule.ReviewRule
# hutool-core hutool-crypto
-keep class
!cn.hutool.core.util.RuntimeUtil,
!cn.hutool.core.util.ClassLoaderUtil,
!cn.hutool.core.util.ReflectUtil,
!cn.hutool.core.util.SerializeUtil,
!cn.hutool.core.util.ClassUtil,
cn.hutool.core.codec.**,
cn.hutool.core.util.**{*;}
-keep class cn.hutool.crypto.**{*;}
-dontwarn cn.hutool.**
# 缓存 Cookie
-keep class **.help.http.CookieStore{*;}
-keep class **.help.CacheManager{*;}
# StrResponse
-keep class **.help.http.StrResponse{*;}

# markwon
-dontwarn org.commonmark.ext.gfm.**

-keep class okhttp3.*{*;}
-keep class okio.*{*;}
-keep class com.jayway.jsonpath.*{*;}

# LiveEventBus
-keepclassmembers class androidx.lifecycle.LiveData {
    *** mObservers;
    *** mActiveCount;
}
-keepclassmembers class androidx.arch.core.internal.SafeIterableMap {
    *** size();
    *** putIfAbsent(...);
}

## ChangeBookSourceDialog initNavigationView
-keepclassmembers class androidx.appcompat.widget.Toolbar {
    *** mNavButtonView;
}

# MenuExtensions applyOpenTint
-keepnames class androidx.appcompat.view.menu.SubMenuBuilder
-keep class androidx.appcompat.view.menu.MenuBuilder {
    *** setOptionalIconsVisible(...);
    *** getNonActionItems();
}

# FileDocExtensions.kt treeDocumentFileConstructor
-keep class androidx.documentfile.provider.TreeDocumentFile {
    <init>(...);
}

# JsoupXpath
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.AxisSelector{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.NodeTest{*;}
-keep,allowobfuscation class * implements org.seimicrawler.xpath.core.Function{*;}

## JSOUP
-keep class org.jsoup.**{*;}
-dontwarn org.jspecify.annotations.NullMarked

## ExoPlayer 反射设置ua 保证该私有变量不被混淆
-keepclassmembers class androidx.media3.datasource.cache.CacheDataSource$Factory {
    *** upstreamDataSourceFactory;
}
## ExoPlayer 如果还不能播放就取消注释这个
# -keep class com.google.android.exoplayer2.** {*;}

## 对外提供api
-keep class io.legado.app.api.ReturnData{*;}

# Cronet
-keepclassmembers class org.chromium.net.X509Util {
    *** sDefaultTrustManager;
    *** sTestTrustManager;
}

# Throwable
-keepnames class * extends java.lang.Throwable
-keepclassmembernames,allowobfuscation class * extends java.lang.Throwable{*;}

# Sora Editor
-keep class org.eclipse.tm4e.** { *; }
-keep class org.joni.** { *; }

# GSYVideoPlayer
-keep class com.shuyu.gsyvideoplayer.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.**
#-keep class com.shuyu.gsyvideoplayer.video.** { *; }
#-dontwarn com.shuyu.gsyvideoplayer.video.**
#-keep class com.shuyu.gsyvideoplayer.video.base.** { *; }
#-dontwarn com.shuyu.gsyvideoplayer.video.base.**
#-keep class com.shuyu.gsyvideoplayer.utils.** { *; }
#-dontwarn com.shuyu.gsyvideoplayer.utils.**
#-keep class com.shuyu.gsyvideoplayer.player.** {*;}
#-dontwarn com.shuyu.gsyvideoplayer.player.**
#-keep class tv.danmaku.ijk.** { *; }
#-dontwarn tv.danmaku.ijk.**
#-keep class androidx.media3.** {*;}
#-keep interface androidx.media3.**
#-keep class com.shuyu.alipay.** {*;}
#-keep interface com.shuyu.alipay.**
-keep public class * extends android.view.View{
    *** get*();
    void set*(***);
    public <init>(android.content.Context);
    public <init>(android.content.Context, java.lang.Boolean);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# LangChain4j - 保留Tool注解和相关类
-keep class io.legado.app.help.ai.langchain4j.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class * {
    @dev.langchain4j.agent.tool.Tool <methods>;
}

# LangChain4j - 保留OpenAI模型类及其字段（防止model参数丢失）
-keep class dev.langchain4j.model.openai.** { *; }
-keep class dev.langchain4j.model.chat.** { *; }
-keep class dev.ai4j.openai4j.** { *; }
-keepclassmembers class dev.langchain4j.model.openai.OpenAiChatModel {
    <fields>;
}

# LangChain4j 1.x - 忽略Android不支持的java.net.http类
# Android使用OkHttp作为HTTP客户端，不需要java.net.http
-dontwarn java.net.http.**
-dontnote java.net.http.**

# Retrofit - 保留泛型签名（LangChain4j内部使用Retrofit）
-keepattributes Signature
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# OkHttp - 保留内部类（LangChain4j 依赖）
-keep class okhttp3.** { *; }
-keepclassmembers class okhttp3.** {
    <fields>;
    <methods>;
}
-dontwarn okhttp3.internal.**

# LiquidGlass - 防止混淆导致空指针异常
-keep class com.qmdeve.liquidglass.** { *; }
-keepclassmembers class com.qmdeve.liquidglass.** {
    <fields>;
    <methods>;
}
-dontwarn com.qmdeve.liquidglass.**
