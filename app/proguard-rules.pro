# kotlinx.serialization keeps its generated serializers via reflection-free
# lookups, but R8 still needs the companions and the @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.arn.aplacetosit.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.arn.aplacetosit.core.**$$serializer { *; }
-keep class com.arn.aplacetosit.core.** { *; }
