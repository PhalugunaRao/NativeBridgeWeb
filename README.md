# Advanced Android WebView SDK

A production-oriented, Kotlin-first Android WebView framework for PWA and hybrid apps. It wraps Android `WebView` with browser-grade defaults: lifecycle safety, permission automation, PWA support, file uploads, downloads, session handling, JavaScript messaging, URL policy enforcement, connectivity state, and clean APIs for XML and programmatic integration.

## High-Level Architecture

```text
app
  sample integration using XML AdvancedWebView, permission manager, and JS bridge

webview-core
  AdvancedWebView container
  SecureWebView baseline
  WebViewClient and WebChromeClient
  browser state, lifecycle, pull-to-refresh, progress UI
  downloads, headers, sessions, connectivity, PWA setup, URL policy

webview-permissions
  WebViewPermissionManager
  Android runtime permissions
  geolocation, camera, microphone, Android 13+ media permissions
  file chooser integration
  rationale and settings dialogs

webview-jsbridge
  secure window.Android bridge
  JSON messaging, async coroutine handlers, Promise callbacks
  native-to-web events

webview-security
  audit helpers, URL validation, enterprise SSL pinning policy helpers
```

The SDK keeps contracts in `webview-core` and optional implementations in separate modules. This avoids dependency cycles and lets enterprise apps replace permission UI, request interception, downloads, or security policy without forking the WebView itself.

## Folder Structure

```text
webview-core/src/main/java/com/phalu/webview/core/
  AdvancedWebView.kt
  AdvancedWebViewContracts.kt
  SecureWebView.kt
  CookieUtils.kt
  WebViewConfig.kt
  client/
    AdvancedWebViewClient.kt
    AdvancedWebChromeClient.kt
  config/
    AdvancedWebViewConfig.kt
  download/
    AndroidDownloadController.kt
  network/
    ConnectivityObserver.kt
    HeaderManager.kt
  pwa/
    PwaManager.kt
  security/
    UrlPolicy.kt
  session/
    SessionManager.kt

webview-permissions/src/main/java/com/phalu/webview/permissions/
  WebViewPermissionManager.kt
  PermissionMapper.kt
  model/
    PermissionModels.kt
  ui/
    PermissionUiDelegate.kt

webview-jsbridge/src/main/java/com/phalu/webview/jsbridge/
  JsBridge.kt

webview-security/src/main/java/com/phalu/webview/security/
  SecurityUtils.kt
  EnterpriseSecurityPolicy.kt
```

## Capabilities

- PWA-ready settings: JavaScript, DOM storage, IndexedDB/database support, service workers, cache mode, local storage, media playback, and offline-aware state.
- Browser features: multiple windows, fullscreen video, file uploads, pull-to-refresh, progress indicator, retry-ready state, back/forward navigation, dark mode, custom user agent, and WebView state save/restore.
- Permissions: camera, microphone, location, photos, videos, audio, notifications, Bluetooth, storage compatibility, rationale UI, permanently-denied detection, app settings redirect, callbacks, and Android 13+ media permissions.
- Network and sessions: dynamic headers, request interception contract, cookie setup, secure cookie helpers, session clearing, OkHttp certificate pinning helper, and connectivity observer.
- Security: safe defaults, mixed content blocking, Safe Browsing toggle, file access restrictions, URL allowlist/blocklist, external scheme routing, SSL error cancellation, bridge host allowlist, and security audit utilities.
- Integration: XML view, programmatic API, lifecycle-aware controller, `StateFlow<BrowserState>`, and a Compose integration path through `AndroidView`.

## Installation

```kotlin
dependencies {
    implementation(project(":webview-core"))
    implementation(project(":webview-permissions"))
    implementation(project(":webview-jsbridge"))
    implementation(project(":webview-security"))
}
```

The SDK min SDK is 24. The sample app targets SDK 36.

## XML Integration

```xml
<com.phalu.webview.core.AdvancedWebView
    android:id="@+id/advanced_webview"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var webView: AdvancedWebView
    private lateinit var permissions: WebViewPermissionManager
    private lateinit var bridge: JsBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissions = WebViewPermissionManager.from(this)
        webView = findViewById(R.id.advanced_webview)

        val config = AdvancedWebViewConfig(
            url = "https://example.com",
            headers = mapOf(
                "Authorization" to "Bearer token",
                "Device" to "Android",
            ),
            permissions = WebPermissions(
                camera = true,
                microphone = true,
                location = true,
                photos = true,
                videos = true,
                notifications = true,
            ),
            settings = WebViewSettings(
                javaScriptEnabled = true,
                domStorageEnabled = true,
                supportMultipleWindows = true,
            ),
            security = SecurityConfig(
                allowedHosts = setOf("example.com"),
            ),
        )

        bridge = JsBridge(trustedHosts = setOf("example.com"))
        bridge.attachToWebView(webView.webView)

        webView.configure(
            config = config,
            lifecycleOwner = this,
            permissionHandler = permissions,
            fileChooserHandler = permissions,
            callback = object : AdvancedWebViewCallback {
                override fun onPageFinished(url: String) {
                    bridge.injectRuntime()
                }
            },
        )
    }
}
```

## Programmatic API

```kotlin
val advancedWebView = AdvancedWebView(context).configure(
    config = AdvancedWebViewConfig(
        url = "https://example.com",
        headers = mapOf("Authorization" to "Bearer token"),
        permissions = WebPermissions(camera = true, location = true),
        settings = WebViewSettings(javaScriptEnabled = true),
    ),
    lifecycleOwner = lifecycleOwner,
    permissionHandler = permissionManager,
    fileChooserHandler = permissionManager,
)
```

## Compose Integration

Use the core view through Compose `AndroidView`:

```kotlin
AndroidView(
    factory = { context ->
        AdvancedWebView(context).configure(
            config = config,
            lifecycleOwner = lifecycleOwner,
            permissionHandler = permissionManager,
            fileChooserHandler = permissionManager,
        )
    },
    update = { view ->
        view.loadUrl(config.url, config.headers)
    },
)
```

## JavaScript Bridge

Web to Android:

```javascript
window.Android.postMessage({
  type: "login",
  payload: { token: "123" }
});

const device = await window.Android.request("getDevice", {});
```

Android:

```kotlin
val bridge = JsBridge(trustedHosts = setOf("example.com"))
bridge.registerHandler("getDevice") {
    JSONObject()
        .put("platform", "android")
        .put("sdk", "AdvancedWebView")
}
bridge.emit("sessionChanged", JSONObject().put("loggedIn", true))
```

Call `bridge.injectRuntime()` from `AdvancedWebViewCallback.onPageFinished` if the page needs the Promise helper on every navigation. The native `Android.postMessage(String)` interface remains attached by WebView, while the helper script normalizes object messages and installs `window.Android.request(...)`.

## Permission Flow

Developers configure desired capabilities through `WebPermissions`. The SDK then:

1. Maps WebView resources to Android runtime permissions.
2. Checks existing grants.
3. Shows rationale UI when Android recommends it.
4. Requests one or more runtime permissions.
5. Persists "asked before" state to detect permanently denied permissions.
6. Opens the app settings screen when the permission cannot be requested again.
7. Grants or denies the pending WebView media or geolocation request.
8. Returns callbacks through `PermissionCallbacks`.

Customize UI by implementing `PermissionUiDelegate`.

## Network, Headers, and Sessions

- Initial headers are passed to `WebView.loadUrl`.
- Dynamic headers can be supplied through `RequestInterceptor.headersFor(url)`.
- Request inspection or custom cached responses can be implemented through `RequestInterceptor.intercept(request)`.
- Use `SessionManager` or `CookieUtils` for auth cookies because Android WebView does not apply custom headers to every subresource request.
- `EnterpriseSecurityPolicy.certificatePinner()` builds an OkHttp `CertificatePinner` for native networking that mirrors the SDK security config.

## Security Recommendations

- Prefer HTTPS and set `SecurityConfig(blockCleartextMainFrameLoads = true)` for production.
- Use `allowedHosts` for enterprise apps that should not navigate outside a known domain set.
- Keep `allowFileAccess = false` unless the app has a deliberate local-content use case.
- Use secure, HttpOnly cookies for session tokens.
- Keep bridge host allowlists tight and expose small, typed message handlers.
- Never call `SslErrorHandler.proceed()` for production traffic. This SDK cancels SSL errors by default.
- Run `SecurityUtils.auditWebView(webView)` in debug builds.

## Common Pitfalls

- Custom headers are not a universal request interceptor in Android WebView. Use cookies or a backend session for ongoing auth.
- File upload requires an Activity or Fragment registered permission manager because Android uses Activity Result APIs.
- Service worker behavior depends on the installed Android System WebView or Chrome provider.
- Downloads to public storage rely on `DownloadManager`; private enterprise downloads should implement a custom downloader.
- Do not keep a WebView in a static singleton. Use `AdvancedWebView.configure(..., lifecycleOwner = ...)`.
- Do not expose broad native APIs through JavaScript. Treat the bridge as an external interface.

## Testing Strategy

- Unit test URL policy, header merging, permission state transitions, and bridge JSON parsing.
- Instrument WebView flows with a local test server for uploads, downloads, geolocation, multiple windows, and offline service workers.
- Run security audit checks in debug builds.
- Test on Android 8, 10, 12, 13, 14, and current target SDK behavior.
- Validate with different WebView providers, especially Chrome stable and Android System WebView.

## Build Process

```bash
./gradlew :app:compileDebugKotlin
./gradlew :webview-core:assemble
./gradlew :webview-permissions:assemble
./gradlew :webview-jsbridge:assemble
./gradlew :webview-security:assemble
```

## Build SDK Artifacts

To generate distributable AARs, extracted JARs, and a local Maven repository in one zip:

```bash
./gradlew packageSdkRelease
```

Output:

```text
build/distributions/NativeBridgeWeb-1.0.0-android-sdk.zip
```

For the full publishing process, see [SDK_PUBLISHING.md](SDK_PUBLISHING.md).

## Future Scaling Ideas

- Add a dedicated `webview-downloader` module with OkHttp streaming, resumable downloads, and authenticated requests.
- Add a `webview-storage` module for encrypted session snapshots and explicit cache policies.
- Publish a Compose artifact once the app chooses a Compose BOM.
- Add Hilt and Koin integration artifacts for apps that want dependency injection.
- Add a PWA manifest parser and install prompt abstraction.
- Add WebAuthn and passkey helpers when the target WebView provider supports the required APIs.
- Add a full browser tab manager for apps that need multiple independent WebViews.

## Current Verification

`./gradlew :app:compileDebugKotlin` passes.
