package com.phalu.webview.core.network

import java.util.concurrent.ConcurrentHashMap

class HeaderManager(
    initialHeaders: Map<String, String> = emptyMap(),
) {
    private val staticHeaders = ConcurrentHashMap<String, String>().apply {
        putAll(initialHeaders)
    }
    private val dynamicProviders = mutableListOf<(String) -> Map<String, String>>()

    @Synchronized
    fun addProvider(provider: (url: String) -> Map<String, String>) {
        dynamicProviders += provider
    }

    fun set(name: String, value: String) {
        staticHeaders[name] = value
    }

    fun remove(name: String) {
        staticHeaders.remove(name)
    }

    fun clear() {
        staticHeaders.clear()
    }

    @Synchronized
    fun headersFor(url: String): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        merged.putAll(staticHeaders)
        dynamicProviders.forEach { provider ->
            merged.putAll(provider(url))
        }
        return merged
    }
}
