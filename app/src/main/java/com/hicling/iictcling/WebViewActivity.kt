package com.hicling.iictcling

import android.os.Bundle
import android.util.Log
import wendu.webviewjavascriptbridge.WVJBWebView

class WebViewActivity : JsActivity() {
    private val tag = this.javaClass.canonicalName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val mWebView: WVJBWebView = findViewById(R.id.webview)
        val bundle = this.intent.extras
        val url = bundle?.get("url").toString()
        val loading = bundle?.get("loading") as Boolean
        Log.i(tag, "Loading url: $url")
        // 初始化
        this.initWebView(url, mWebView, null, loading)
    }
}