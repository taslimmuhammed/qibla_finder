# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Adhan prayer times library (uses reflection)
-keep class com.batoulapps.adhan.** { *; }

# Keep RemoteViews IDs for notification layout
-keep class com.taslim.qiblafinder.R$id { *; }
-keep class com.taslim.qiblafinder.R$layout { *; }
-keep class com.taslim.qiblafinder.R$drawable { *; }

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**