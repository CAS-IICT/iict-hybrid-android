/*
*
* devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* IICT copyright
* 中国科学院计算所苏州
* MIT License
* All activities with webview component need to extend this class
*/

package com.hicling.iictcling

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Toast
import com.google.gson.Gson
import wendu.webviewjavascriptbridge.WVJBWebView

@SuppressLint("Registered")
open class JsActivity : Activity() {
    private var mWebView: WVJBWebView? = null
    private var splashView: LinearLayout? = null
    private var loading = false
    private val tag = this.javaClass.canonicalName

    @SuppressLint("SetJavaScriptEnabled")
    open fun initWebView(
        url: String,
        mWebView: WVJBWebView? = null,
        splashView: LinearLayout? = null,
        loading: Boolean = false
    ) {
        this.mWebView = mWebView
        this.splashView = splashView
        this.loading = loading

        if (mWebView != null) {
            showLoading(loading)
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

            mWebView.overScrollMode = WVJBWebView.OVER_SCROLL_NEVER // 取消WebView中滚动或拖动到顶部、底部时的阴影
            mWebView.scrollBarStyle = WVJBWebView.SCROLLBARS_INSIDE_OVERLAY // 取消滚动条白边效果
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

            //deal with the error network
            mWebView.webViewClient = object : WebViewClient() {

                override fun onPageFinished(view: WebView?, url: String?) {
                    showLoading(false)
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

    // give standard response as JSON to frontend
    open fun json(status: Int, data: Any? = null, msg: String = ""): String {
        var obj: String? = null
        if (data != null) {
            obj = Gson().toJson(data)
        }
        return Gson().toJson(ResData(status, obj, msg))
    }

    // use to init all the bridge functions to handle js call, only for the activities which extends jsActivity
    private fun initBridgeFunc() {
        Log.i(tag, "initBridgeFunc")
        // back
        mWebView?.registerHandler("back", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call back")
            if (mWebView!!.canGoBack()) {
                mWebView?.goBack()
                function.onResult(json(1))
            } else {
                function.onResult(json(1))
                this.finish()
            }
        })
        // toast
        mWebView?.registerHandler("toast", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call toast")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), ToastData::class.java)
            val toast = Toast.makeText(this, data.text, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.BOTTOM, 0, 100)
            toast.show()
            function.onResult(json(1))
        })
        // alert
        mWebView?.registerHandler("alert", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call alert")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), AlertData::class.java)
            AlertDialog.Builder(this)
                .setMessage(data.message)
                .setTitle(data.title)
                .setPositiveButton(data.btnConfirm) { _, _ ->
                    function.onResult(json(1))
                }.create().show()
        })
        // loading
        mWebView?.registerHandler("loading", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call loading")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), LoadingData::class.java)
            showLoading(data.load)
            function.onResult(json(1))
        })
        // set status bar
        mWebView?.registerHandler("setStatusBar",
            WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
                Log.i(tag, "js call set navbar")
                Log.i(tag, data.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Log.i(tag, "call set navbar success")
                    val data = Gson().fromJson(data.toString(), StatusBarData::class.java)
                    val color = Color.parseColor(data.color)
                    Log.i(tag, color.toString())
                    this.window.statusBarColor = color
                    function.onResult(json(1))
                } else {
                    Log.i(tag, "call set navbar fail")
                    function.onResult(json(0, null, "Low Version, need more than Android5.0"))
                }
            })
        // start a new webview activity and go to the specified url
        mWebView?.registerHandler("go", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call go other webview, new activity")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), WebViewData::class.java)
            goWebView(data.url, data.loading)
            function.onResult(json(1, null, data.url))
        })
    }

    // go to other activity
    private fun goWebView(url: String, loading: Boolean = false) {
        val intent = Intent(this, WebViewActivity::class.java)
        val bundle = Bundle()
        bundle.putString("url", url)
        bundle.putBoolean("loading", loading)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun showLoading(load: Boolean) {
        val modal: LinearLayout = findViewById(R.id.load)
        if (load) modal.visibility = View.VISIBLE
        else modal.visibility = View.INVISIBLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mWebView!!.canGoBack()) {
            mWebView?.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

}

