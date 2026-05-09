package com.phalu.webview.jsbridge

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class JsBridge(
    private val trustedHosts: Set<String> = emptySet(),
    private val interfaceName: String = "Android",
) {
    private val handlers = ConcurrentHashMap<String, suspend (JSONObject) -> JSONObject>()
    private val subscriptions = ConcurrentHashMap<String, MutableSet<(JSONObject) -> Unit>>()
    private var webViewRef: WeakReference<WebView>? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun attachToWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
        webView.addJavascriptInterface(JsInterface(), interfaceName)
        injectRuntime()
    }

    fun registerHandler(type: String, handler: suspend (JSONObject) -> JSONObject) {
        handlers[type] = handler
    }

    fun registerSyncHandler(type: String, handler: (JSONObject) -> Any?) {
        handlers[type] = { payload ->
            JSONObject().put("value", handler(payload))
        }
    }

    fun unregisterHandler(type: String) {
        handlers.remove(type)
    }

    fun subscribe(type: String, listener: (JSONObject) -> Unit) {
        subscriptions.getOrPut(type) { LinkedHashSet() }.add(listener)
    }

    fun unsubscribe(type: String, listener: (JSONObject) -> Unit) {
        subscriptions[type]?.remove(listener)
    }

    fun emit(type: String, payload: JSONObject = JSONObject()) {
        val message = JSONObject()
            .put("type", type)
            .put("payload", payload)
        evaluate("window.dispatchEvent(new MessageEvent('native-message', { data: $message }));")
    }

    fun callJs(functionName: String, payload: JSONObject = JSONObject(), callback: ((String?) -> Unit)? = null) {
        evaluate("$functionName($payload);", callback)
    }

    fun injectRuntime() {
        val webView = webViewRef?.get() ?: return
        webView.evaluateJavascript(runtimeScript(interfaceName), null)
    }

    private fun evaluate(script: String, callback: ((String?) -> Unit)? = null) {
        val webView = webViewRef?.get() ?: return
        scope.launch {
            webView.evaluateJavascript(script) { callback?.invoke(it) }
        }
    }

    private fun isTrusted(url: String?): Boolean {
        if (trustedHosts.isEmpty()) return true
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return trustedHosts.any { trusted -> host == trusted || host.endsWith(".$trusted") }
    }

    fun destroy() {
        webViewRef?.get()?.removeJavascriptInterface(interfaceName)
        webViewRef?.clear()
        webViewRef = null
        handlers.clear()
        subscriptions.clear()
        scope.cancel()
    }

    private inner class JsInterface {
        @JavascriptInterface
        fun postMessage(raw: String): String {
            val currentUrl = webViewRef?.get()?.url
            if (!isTrusted(currentUrl)) return error(null, "Untrusted origin")

            return runCatching {
                val message = JSONObject(raw)
                val type = message.getString("type")
                val payload = message.optJSONObject("payload") ?: message
                val callbackId = message.optString("callbackId").takeIf { it.isNotBlank() }

                subscriptions[type]?.forEach { listener -> listener(payload) }
                val handler = handlers[type] ?: return@runCatching success(callbackId, JSONObject())

                scope.launch {
                    val result = runCatching { handler(payload) }
                    val script = result.fold(
                        onSuccess = { "window.__AdvancedWebViewBridge.resolve('${callbackId.orEmpty()}', $it);" },
                        onFailure = {
                            "window.__AdvancedWebViewBridge.reject('${callbackId.orEmpty()}', ${JSONObject.quote(it.message ?: "Bridge error")});"
                        },
                    )
                    evaluate(script)
                }
                success(callbackId, JSONObject().put("queued", true))
            }.getOrElse {
                error(null, it.message ?: "Invalid bridge message")
            }
        }
    }

    private fun success(callbackId: String?, data: JSONObject): String {
        return JSONObject()
            .put("success", true)
            .put("callbackId", callbackId)
            .put("data", data)
            .toString()
    }

    private fun error(callbackId: String?, message: String): String {
        return JSONObject()
            .put("success", false)
            .put("callbackId", callbackId)
            .put("error", message)
            .toString()
    }

    private fun runtimeScript(name: String): String {
        val escapedName = JSONObject.quote(name)
        val bridgeId = UUID.randomUUID().toString()
        return """
            (function() {
              if (window.__AdvancedWebViewBridge) return;
              const pending = {};
              window.__AdvancedWebViewBridge = {
                id: ${JSONObject.quote(bridgeId)},
                resolve: function(id, value) {
                  if (!pending[id]) return;
                  pending[id].resolve(value);
                  delete pending[id];
                },
                reject: function(id, message) {
                  if (!pending[id]) return;
                  pending[id].reject(new Error(message));
                  delete pending[id];
                }
              };
              const nativeBridge = window[$escapedName];
              window.Android = window.Android || {};
              window.Android.postMessage = function(message) {
                const normalized = typeof message === 'string' ? message : JSON.stringify(message || {});
                return nativeBridge.postMessage(normalized);
              };
              window.Android.request = function(type, payload) {
                const callbackId = Date.now().toString(36) + Math.random().toString(36).slice(2);
                return new Promise(function(resolve, reject) {
                  pending[callbackId] = { resolve: resolve, reject: reject };
                  nativeBridge.postMessage(JSON.stringify({ type: type, payload: payload || {}, callbackId: callbackId }));
                });
              };
            })();
        """.trimIndent()
    }
}
