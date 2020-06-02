/*
* iict devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* 主activity，其中要包含clingsdk的一些方法，继承自jsActivity，获得父类全部基础的webviewbridge注册方法
 */
package com.hicling.iictcling

import android.os.Bundle
import android.util.Log
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

class MainActivity : JsActivity() {
    private val url: String = "http://192.168.1.79:8080"
    private val appID: String = "HCd176b8b47b3ed84c"
    private val appSecret: String = "92f767d18c3e843bb23e317617c55175"
    private val tag: String = this.javaClass.canonicalName.toString()
    private var deviceInfo: PERIPHERAL_DEVICE_INFO_CONTEXT? = null
    private val scanTime: Int = 3000
    private var mWebView: WVJBWebView? = null

    // sdk is ready
    private val clingReady = object : OnSdkReadyListener {
        override fun onClingSdkReady() {
            Log.i(tag, "SDK is ready")
            // init bridge functions related to sdk after success
            initBridge()
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

    private val mDeviceConnectedListener =
        object : OnDeviceConnectedListener {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val mWebView: WVJBWebView = findViewById(R.id.webview)
        val splashView: LinearLayout = findViewById(R.id.splash)
        this.mWebView = mWebView
        // 初始化
        this.initWebView(url, mWebView, splashView)
        this.initCling()
    }

    // these bridge functions can only used in this activity
    private fun initBridge() {
        Log.i(tag, "Init more functions for sdk")
        // sdk sign in
        mWebView?.registerHandler("signIn", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
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
        // sdk sign up
        mWebView?.registerHandler("signUp", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
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

        // start scan devices
        mWebView?.registerHandler("startScan", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call start scanning")
            ClingSdk.stopScan()
            if (!ClingSdk.isAccountBondWithCling()) {
                ClingSdk.setClingDeviceType(ClingSdk.CLING_DEVICE_TYPE_ALL)
            }
            Log.i(tag, "scan time $scanTime")
            ClingSdk.startScan(scanTime) { o ->
                //蓝牙连接成功后，不会再扫描
                Log.i(tag, "onBleScanUpdated()")

                if (o != null) {
                    function.onResult(json(1, o, "scan success"))
                }

            }
        })
        // stop scan devices
        mWebView?.registerHandler("stopScan", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call stop scan")
            ClingSdk.stopScan()
            function.onResult(json(1))
        })
    }

    private fun initCling() {
        ClingSdk.init(this, appID, appSecret, clingReady)
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
