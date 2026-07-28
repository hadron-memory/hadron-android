# R8 rules for the release build.
#
# kotlinx-serialization, OkHttp, Compose, and Coil all ship consumer rules, so
# the defaults cover the app. The one thing R8's full mode can strip is a
# @Serializable class that is only ever reached through its generated
# serializer — keep those companions explicitly.
-keepclasseswithmembers class * {
    public static ** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# OkHttp references these optional platform APIs behind availability checks.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
