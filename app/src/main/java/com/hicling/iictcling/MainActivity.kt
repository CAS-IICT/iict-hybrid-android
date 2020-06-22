/*
* iict devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* 主activity，其中要包含clingsdk的一些方法，继承自jsActivity，获得父类全部基础的webviewbridge注册方法
 */
package com.hicling.iictcling

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.google.gson.Gson
import com.hicling.clingsdk.ClingSdk
import com.hicling.clingsdk.devicemodel.PERIPHERAL_DEVICE_INFO_CONTEXT
import com.hicling.clingsdk.listener.OnBleListener.*
import com.hicling.clingsdk.listener.OnNetworkListener
import com.hicling.clingsdk.model.DayTotalDataModel
import com.hicling.iictcling.notification.SysNotificationListenerService
import wendu.webviewjavascriptbridge.WVJBWebView
import wendu.webviewjavascriptbridge.WVJBWebView.WVJBHandler


class MainActivity : WebViewActivity() {
    override var url = "http://192.168.1.79:8080"

    //override var url = "http://192.168.1.103:8080" //前端
    private var deviceInfo: PERIPHERAL_DEVICE_INFO_CONTEXT? = null
    private var splashView: LinearLayout? = null

    private val content = R.layout.activity_main
    private val appID = "HCe0f4ae28e21efffd"//企业
    private val appSecret = "9a3438f7b77968c4524b1a427cd80522"//企业

    //private val appID: String = "HCd176b8b47b3ed84c"//个人
    //private val appSecret: String = "92f767d18c3e843bb23e317617c55175"//个人
    override val tag = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(content)
        splashView = findViewById(R.id.splash)
        findViewById<WVJBWebView>(R.id.webview)?.let {
            mWebView = it
            initWebView(it)
            initCling()
            initBridge(it)
        }
    }

    private fun initCling() {
        val intent = Intent(this, SysNotificationListenerService::class.java)
        startService(intent)
        ClingSdk.init(App.getContext(), appID, appSecret, clingReady)
        ClingSdk.setBleDataListener(bleDataListener)
        ClingSdk.setDeviceConnectListener(mDeviceConnectedListener)
        ClingSdk.deregisterDevice(deregisterDeviceListener)
        ClingSdk.enableDebugMode(true)
        ClingSdk.start(App.getContext())
    }

    private val deregisterDeviceListener: OnDeregisterDeviceListener =
        object : OnDeregisterDeviceListener {
            override fun onDeregisterDeviceSucceed() {
                Log.i(tag, "onDeregisterDeviceSucceed()")
                ClingSdk.clearDatabase()
                //            Main3Activity.this.finish();
            }

            override fun onDeregisterDeviceFailed(i: Int, s: String) {
                Log.i(tag, "onDeregisterDeviceFailed(): $i, $s")
            }
        }

    // sdk is ready
    private val clingReady = object : OnNetworkListener {
        override fun onSucceeded(p0: Any?, p1: Any?) {
            Log.i(tag, "SDK is ready")
        }

        override fun onFailed(p0: Int, p1: String?) {
            Log.i(tag, "onClingSdkReady onFailed $p0")
        }
        /*
        override fun onClingSdkReady() {
            Log.i(tag, "SDK is ready")
            // init bridge functions related to sdk after success
        }

        override fun onFailed(p0: String?) {
            Log.i(tag, "onClingSdkReady onFailed $p0")
        }
        */
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

        override fun onGetDayTotalData(dayTotalDataModel: DayTotalDataModel?) {
            Log.i(tag, "getDayTotalData") //MinuteData
        }
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

    // these bridge functions can only used in this activity, not global and general
    private fun initBridge(mWebView: WVJBWebView) {
        Log.i(tag, "Init clingsdk in bridge")
        // sdk sign in
        mWebView.registerHandler("signIn", WVJBHandler<Any?, Any?> { data, function ->
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
        // sdk sign out
        mWebView.registerHandler("signOut", WVJBHandler<Any?, Any?> { data, function ->
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
        mWebView.registerHandler("signUp", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call signUp")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SignUpData::class.java)
            ClingSdk.signUp(
                data.username,
                data.password,
                data.rePassword,
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
        // get cling sdk user info
        mWebView.registerHandler(
            "getClingUserInfo",
            WVJBHandler<Any?, Any?> { _, function ->
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
        mWebView.registerHandler("connect", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call connect to cling device")
        })

        // start scan cling devices
        mWebView.registerHandler("startScan", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call start scanning devices")
            ClingSdk.stopScan()
            if (!ClingSdk.isAccountBondWithCling()) {
                ClingSdk.setClingDeviceType(ClingSdk.CLING_DEVICE_TYPE_ALL)
            }
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), TimeData::class.java)
            ClingSdk.startScan(data.time.toInt()) { obj ->
                Log.i(tag, obj.toString())
                function.onResult(json(1, obj, "list devices"))
            }
        })

        // stop scan cling devices
        mWebView.registerHandler("stopScan", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call stop scan devices")
            ClingSdk.stopScan()
            function.onResult(json(1, null, "stop scan"))
        })
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
