# kotlinx.serialization: keep generated serializers for payloads and navigation routes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class dev.housecalendar.** {
    *** Companion;
}
-keepclasseswithmembers class dev.housecalendar.** {
    kotlinx.serialization.KSerializer serializer(...);
}
