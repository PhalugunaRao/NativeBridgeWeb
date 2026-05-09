# JavaScript interfaces are called reflectively by Android WebView.
-keepclassmembers class com.phalu.webview.jsbridge.** {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.phalu.webview.jsbridge.** { public *; }

