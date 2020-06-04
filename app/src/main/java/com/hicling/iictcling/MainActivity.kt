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
import com.hicling.clingsdk.ClingSdk
import com.hicling.clingsdk.devicemodel.PERIPHERAL_DEVICE_INFO_CONTEXT
import com.hicling.clingsdk.listener.OnBleListener.OnBleDataListener
import com.hicling.clingsdk.listener.OnBleListener.OnDeviceConnectedListener
import com.hicling.clingsdk.listener.OnNetworkListener
import com.hicling.clingsdk.listener.OnSdkReadyListener
import com.hicling.clingsdk.model.DayTotalDataModel
import wendu.webviewjavascriptbridge.WVJBWebView

class MainActivity : WebViewActivity() {
    override var url: String = "http://192.168.1.79:8080"
    private var content: Int = R.layout.activity_main
    private val appID: String = "HCd176b8b47b3ed84c"
    private val appSecret: String = "92f767d18c3e843bb23e317617c55175"
    override val tag: String = this.javaClass.simpleName
    private var deviceInfo: PERIPHERAL_DEVICE_INFO_CONTEXT? = null
    private val scanTime: Int = 15000
    private var splashView: LinearLayout? = null
    private var mWebView: WVJBWebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(content)
        startService(this.intent)
        splashView = findViewById(R.id.splash)
        findViewById<WVJBWebView>(R.id.webview)?.let {
            mWebView = it
            initWebView(it)
            initCling()
        }
    }

    // sdk is ready
    private val clingReady = object : OnSdkReadyListener {
        override fun onClingSdkReady() {
            Log.i(tag, "SDK is ready")
            // init bridge functions related to sdk after success
            mWebView?.let {
                initBridge(it)
            }
        }

        override fun onFailed(p0: String?) {
            Log.i(tag, "onClingSdkReady onFailed $p0")
        }
    }

    //listen bluetooth
    private val bleDataListener = object : OnBleDataListener {
        override fun onGotSosMessage() {
            Log.i(tag, "received sos message")
        }

        override fun onDataSyncingProgress(o: Any) {
            Log.i(tag, "onDataSyncingProgress $o")
        }

        override fun onDataSyncedFromDevice() {
            Log.i(tag, "data synced")
        }

        override fun onDataSyncingMinuteData(o: Any?) {
            Log.i(tag, "onDataSyncingMinuteData is: $o") //MinuteData
        }

        override fun onGetDayTotalData(dayTotalDataModel: DayTotalDataModel?) {}
    }

    private val mDeviceConnectedListener = object : OnDeviceConnectedListener {
        override fun onDeviceConnected() {
            Log.i(tag, "onDeviceConnected()")
        }

        override fun onDeviceDisconnected() {
            Log.i(tag, "onDeviceDisconnected()")
        }

        override fun onDeviceInfoReceived(o: Any) {
            deviceInfo = o as PERIPHERAL_DEVICE_INFO_CONTEXT
            Log.i(tag, "onDeviceInfoReceived: " + deviceInfo?.softwareVersion)
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

    // these bridge functions can only used in this activity
    private fun initBridge(mWebView: WVJBWebView) {
        Log.i(tag, "Init more functions for sdk")
        // sdk sign in
        mWebView.registerHandler("signIn", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call signIn")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SignInData::class.java)
            ClingSdk.signIn(data.username, data.password, object : OnNetworkListener {
                override fun onSucceeded(p0: Any?, p1: Any?) {
                    Log.i(tag, "Sign in successfully")
                    function.onResult(json(1, null, "Sign in success"))
                }

                override fun onFailed(p0: Int, p1: String?) {
                    Log.i(tag, "SignIn Failed:$p0, $p1")
                    function.onResult(json(0, null, p1.toString()))
                }
            })
        })
        mWebView.registerHandler("signOut", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call signOut")
            Log.i(tag, data.toString())
            ClingSdk.signOut(object : OnNetworkListener {
                override fun onSucceeded(p0: Any?, p1: Any?) {
                    function.onResult(json(1, null, "Sign out success"))
                }

                override fun onFailed(p0: Int, p1: String?) {
                    function.onResult(json(0, null, "Sign out fail"))
                }
            })
        })

        // sdk sign up
        mWebView.registerHandler("signUp", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call signUp")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SignUpData::class.java)
            ClingSdk.signUp(
                data.username,
                data.password,
                data.repassword,
                object : OnNetworkListener {
                    override fun onSucceeded(p0: Any?, p1: Any?) {
                        Log.i(tag, "Sign up successfully")
                        function.onResult(json(1, null, "Sign up success"))
                    }

                    override fun onFailed(p0: Int, p1: String?) {
                        Log.i(tag, "SignIn Failed: $p0, $p1")
                        function.onResult(json(0, null, p1.toString()))
                    }
                })
        })
        mWebView.registerHandler(
            "getClingUserInfo",
            WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
                ClingSdk.requestUserProfile(object : OnNetworkListener {
                    override fun onSucceeded(p0: Any?, p1: Any?) {
                        Log.i(tag, "getClingUserInfo call back success")
                        function.onResult(json(1, p0, p1.toString()))
                    }

                    override fun onFailed(p0: Int, p1: String?) {
                        Log.i(tag, "getClingUserInfo call back fail")
                        function.onResult(json(1, null, p1.toString()))
                    }
                })
            })

        // connect devices
        mWebView.registerHandler("connect", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call connect to cling device")
        })

        // start scan devices
        mWebView.registerHandler("startScan", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call start scanning")
            ClingSdk.stopScan()
            ClingSdk.setClingDeviceType(ClingSdk.CLING_DEVICE_TYPE_ALL)
            ClingSdk.startScan(scanTime) { p0 ->
                Log.i(tag, p0.toString())
                function.onResult(json(1, p0, "list devices"))
            }
        })

        // stop scan devices
        mWebView.registerHandler("stopScan", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call stop scan")
            ClingSdk.stopScan()
            function.onResult(json(1))
        })
    }

    private fun initCling() {
        ClingSdk.init(App.getContext(), appID, appSecret, clingReady)
        ClingSdk.setBleDataListener(bleDataListener)
        ClingSdk.setDeviceConnectListener(mDeviceConnectedListener)
        ClingSdk.enableDebugMode(true)
        ClingSdk.start(this)
    }


    override fun onDestroy() {
        ClingSdk.stop(this)
        Log.i(tag, "onDestroy()")
        super.onDestroy()
    }

    override fun onResume() {
        ClingSdk.onResume(this)
        Log.i(tag, "onResume()")
        super.onResume()
    }

    override fun onPause() {
        Log.i(tag, "onPause()")
        ClingSdk.onPause(this)
        super.onPause()
    }

}
