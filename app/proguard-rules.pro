# Keep the JavaScript bridge used by the offline fallback page.
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView clients and their callbacks.
-keep class org.friendsunityfoundation.app.** { *; }
