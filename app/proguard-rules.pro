# youtubedl-android: keep the library and its Jackson JSON mapping
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**

# yt-dlp unzips Python with Apache Commons Compress, which creates zip extra-field classes by reflection
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.io.**
