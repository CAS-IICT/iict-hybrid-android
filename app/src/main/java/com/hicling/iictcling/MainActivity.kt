/*
* iict devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* 主activity，其中要包含clingsdk的一些方法，继承自jsActivity，获得父类全部基础的webviewbridge注册方法
 */
package com.hicling.iictcling

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.google.gson.Gson
import wendu.webviewjavascriptbridge.WVJBWebView
import wendu.webviewjavascriptbridge.WVJBWebView.WVJBHandler


class MainActivity : WebViewActivity() {
    private var splashView: LinearLayout? = null

    private val content = R.layout.activity_main
    override val tag = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(content)
        splashView = findViewById(R.id.splash)
        findViewById<WVJBWebView>(R.id.webview)?.let {
            mWebView = it
            initWebView(it)
            initBridge(it)
        }
    }

    override fun onLoadError() {
        super.onLoadError()
        splashView?.let {
            Animation().fadeOut(it as View, 1000)
        }
    }

    override fun onLoadFinish() {
        super.onLoadFinish()
        splashView?.let {
            Animation().fadeOut(it as View, 1000)
        }
    }

    // these bridge functions can only used in this activity, not global and general
    override fun initBridge(mWebView: WVJBWebView) {
        super.initBridge(mWebView)
        Log.i(tag, "MainActivity: initBridge")

        mWebView.registerHandler("connectBle", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call connectBle")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BleDeviceData::class.java)
            if (checkBle()) {
                val device = bleDeviceHash[data.mac]
                device?.let {
                }
            }
        })
    }


    override fun onDestroy() {
        Log.i(tag, "onDestroy()")
        super.onDestroy()
    }

    override fun onResume() {
        Log.i(tag, "onResume()")
        super.onResume()
    }

    override fun onPause() {
        Log.i(tag, "onPause()")
        super.onPause()
    }

}
