# El puente JS (WebBridge) se llama por nombre desde la página: no se puede renombrar ni quitar.
-keepclassmembers class com.ld.tubeld.WebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
