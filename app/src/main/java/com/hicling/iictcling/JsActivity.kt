package com.hicling.iictcling

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Toast
import com.github.lzyzsd.jsbridge.BridgeWebView
import com.github.lzyzsd.jsbridge.BridgeWebViewClient
import com.google.gson.Gson

/*
All activities with webview component need to extend this class
 */

@SuppressLint("Registered")
open class JsActivity : Activity() {
    private var mWebView: BridgeWebView? = null
    private var splashView: LinearLayout? = null
    private val tag = this.javaClass.canonicalName

    @SuppressLint("SetJavaScriptEnabled")
    open fun initWebView(
        url: String,
        mWebView: BridgeWebView? = null,
        splashView: LinearLayout? = null
    ) {
        this.mWebView = mWebView
        this.splashView = splashView
        if (mWebView != null) {
            val webSettings: WebSettings = mWebView.settings

            webSettings.setSupportZoom(false)
            webSettings.useWideViewPort = true
            webSettings.loadWithOverviewMode = true
            webSettings.defaultTextEncodingName = "utf-8"
            webSettings.loadsImagesAutomatically = true
            //多窗口
            webSettings.supportMultipleWindows()
            //允许访问文件
            webSettings.allowFileAccess = true
            //开启javascript
            webSettings.javaScriptEnabled = true
            //支持通过JS打开新窗口
            //webSettings.javaScriptCanOpenWindowsAutomatically = true
            //关闭webview中缓存
            webSettings.setAppCacheEnabled(true)
            webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
            webSettings.domStorageEnabled = true //DOM Storage

            mWebView.overScrollMode = BridgeWebView.OVER_SCROLL_NEVER // 取消WebView中滚动或拖动到顶部、底部时的阴影
            mWebView.scrollBarStyle = BridgeWebView.SCROLLBARS_INSIDE_OVERLAY // 取消滚动条白边效果
            //获取触摸焦点
            mWebView.requestFocusFromTouch()
            // >= 19(SDK4.4)启动硬件加速，否则启动软件加速
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                webSettings.loadsImagesAutomatically = true //支持自动加载图片
            } else {
                mWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                webSettings.loadsImagesAutomatically = false
            }

            mWebView.webViewClient = object : BridgeWebViewClient(mWebView) {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (splashView != null) Animation().fadeOut(splashView as View, 1000)
                    super.onPageFinished(view, url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    Log.i(tag, "onReceivedError")
                    view?.loadUrl("file:///android_asset/error.html")
                    super.onReceivedError(view, request, error)
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?
                ) {
                    Log.i(tag, "onReceivedHttpError")
                    view?.loadUrl("file:///android_asset/error.html")
                    super.onReceivedHttpError(view, request, errorResponse)
                }
            }
            // deal with error page for android < 6.0 mash
            mWebView.webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        if (title.contains("404")
                            || title.contains("500")
                            || title.contains("Error")
                            || title.contains("找不到网页")
                            || title.contains("网页无法打开")
                            || title.contains("not available")
                        ) {
                            Log.i(tag, "title find error, android<6.0")
                            view.loadUrl("file:///android_asset/error.html")
                        }
                    }
                }

            }
            mWebView.loadUrl(url)
            initBridgeFunc()
        }
    }

    // use to init all the bridge functions to handle js call
    private fun initBridgeFunc() {
        Log.i(tag, "initBridgeFunc")
        // back
        mWebView?.registerHandler("back") { _, function ->
            Log.i(tag, "js call back")
            if (mWebView!!.canGoBack()) {
                mWebView?.goBack()
                function.onCallBack("back")
            } else {
                this.finish()
            }
        }

        mWebView?.registerHandler("toast") { data, function ->
            Log.i(tag, "js call toast")
            Log.i(tag, data)
            val data = Gson().fromJson(data, ToastData::class.java)
            Log.i(tag, "js call toast")
            val toast = Toast.makeText(this, data.text, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.BOTTOM, 0, 100)
            toast.show()
            function.onCallBack("toast")
        }

        mWebView?.registerHandler("alert") { data, function ->
            Log.i(tag, "js call alert")
            Log.i(tag, data)
            val data = Gson().fromJson(data, AlertData::class.java)
            AlertDialog.Builder(this)
                .setMessage(data.message)
                .setTitle(data.title)
                .setPositiveButton(data.btnConfirm) { _, _ ->
                    function.onCallBack("alert")
                }.create().show()
        }
        mWebView?.registerHandler("loading") { data, function ->
            Log.i(tag, "js call loading")
            Log.i(tag, data)
            val data = Gson().fromJson(data, LoadingData::class.java)
            val modal: LinearLayout = findViewById(R.id.load)
            if (data.load) modal.visibility = View.VISIBLE
            else modal.visibility = View.INVISIBLE
            function.onCallBack("loading")
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView!!.canGoBack()) {
            mWebView?.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

