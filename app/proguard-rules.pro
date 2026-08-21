# R8 removed AppLock and ui.QrUtil from the 1.6 build because nothing referenced them
# from Java (they were reached reflectively / from resources). Pin every parvaz package.
-keep class com.parvaz.tunnel.** { *; }
-keep class com.parvaz.tunnel.core.** { *; }
-keep class com.parvaz.tunnel.ui.** { *; }
-keep class com.parvaz.tunnel.config.** { *; }
-keep class com.parvaz.tunnel.model.** { *; }
-keep class com.parvaz.tunnel.store.** { *; }

# gomobile / libv2ray bridge is called across JNI.
-keep class libv2ray.** { *; }
-keep class go.** { *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable

# zxing embedded looks up the capture activity by name.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
