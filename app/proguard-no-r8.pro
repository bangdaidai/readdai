# ProGuard rules for releaseNoR8 build type
# Enables R8 for dexing (fixes D8 Kotlin metadata bug) but disables all optimization/obfuscation.

# Do not obfuscate class/method names
-dontobfuscate

# Do not optimize code
-dontoptimize

# Do not shrink/remove unused code
-dontshrink

# Keep all attributes needed for reflection
-keepattributes *Annotation*,InnerClasses,Signature,Exceptions,EnclosingMethod

# Keep all classes and members
-keep class ** { *; }

# Parcelize - ensure D8 doesn't crash on metadata
-keep class kotlinx.parcelize.** { *; }
-keep,allowobfuscation @kotlinx.parcelize.Parcelize class *

# Missing class warnings - libraries with optional/alternative implementations
-dontwarn jakarta.json.**
-dontwarn org.apache.tapestry5.json.**
-dontwarn org.codehaus.jettison.json.**
-dontwarn org.chromium.base.FeatureList
-dontwarn org.chromium.base.FeatureMap
-dontwarn org.chromium.base.FeatureParam
-dontwarn org.chromium.base.MutableFlagWithSafeDefault
-dontwarn org.chromium.base.MutableParamWithSafeDefault
-dontwarn org.osgi.framework.**
-dontwarn org.osgi.util.tracker.**
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn kotlin.Cloneable$DefaultImpls
