# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class de.isolarwidget.**$$serializer { *; }
-keepclasseswithmembers class de.isolarwidget.** { kotlinx.serialization.KSerializer serializer(...); }
# Retrofit
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface de.isolarwidget.data.api.IsolarApi
