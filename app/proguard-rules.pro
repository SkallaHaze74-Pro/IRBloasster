# SmartIR compact build.
# AndroidX, Compose and OkHttp provide consumer keep rules; retain only
# runtime metadata used by Kotlin/Compose and JSON/networking reflection.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keep class org.json.** { *; }
