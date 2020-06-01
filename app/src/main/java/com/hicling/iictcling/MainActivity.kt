package com.hicling.iictcling

import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import com.github.lzyzsd.jsbridge.BridgeWebView
import com.google.gson.Gson
import com.hicling.clingsdk.ClingSdk
import com.hicling.clingsdk.devicemodel.PERIPHERAL_DEVICE_INFO_CONTEXT
import com.hicling.clingsdk.listener.OnBleListener.OnBleDataListener
import com.hicling.clingsdk.listener.OnBleListener.OnDeviceConnectedListener
import com.hicling.clingsdk.listener.OnNetworkListener
import com.hicling.clingsdk.model.DayTotalDataModel


class MainActivity : JsActivity() {
    private val url: String = "http://192.168.1.79:8080"
    private val appID: String = "HCd176b8b47b3ed84c"
    private val appSecret: String = "92f767d18c3e843bb23e317617c55175"
    private val tag = this.javaClass.canonicalName
    private var deviceInfo: PERIPHERAL_DEVICE_INFO_CONTEXT? = null
    private var sdkReady: Boolean = false
    private val scanTime = 15000


    // sdk is ready
    private val clingReady = object : OnNetworkListener {
        override fun onSucceeded(p0: Any?, p1: Any?) {
            Log.i(tag, "SDK is ready")
            sdkReady = true
        }

        override fun onFailed(p0: Int, p1: String?) {
            Log.i(tag, "onClingSdkReady onFailed $p1")
            sdkReady = false
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

    private val mDeviceConnectedListener: OnDeviceConnectedListener =
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
        ClingSdk.init(this, appID, appSecret, clingReady)

        ClingSdk.setBleDataListener(bleDataListener)
        ClingSdk.setDeviceConnectListener(mDeviceConnectedListener)
        ClingSdk.enableDebugMode(true)
        ClingSdk.start(this)

        val mWebView: BridgeWebView = findViewById(R.id.webview)
        val splashView: LinearLayout = findViewById(R.id.splash)
        // 初始化
        this.initWebView(url, mWebView, splashView)
        this.register(mWebView)
    }

    private fun register(mWebView: BridgeWebView? = null) {
        mWebView?.registerHandler("startScan") { _, function ->
            Log.i(tag, "js call start scanning")
            if (sdkReady) {
                ClingSdk.stopScan()
                if (!ClingSdk.isAccountBondWithCling()) {
                    ClingSdk.setClingDeviceType(ClingSdk.CLING_DEVICE_TYPE_ALL)
                }
                Log.i(tag, "scan time $scanTime")
                ClingSdk.startScan(scanTime) { o ->
                    //蓝牙连接成功后，不会再扫描
                    Log.i(tag, "onBleScanUpdated()")

                    if (o != null) {
                        function.onCallBack(Gson().toJson(o))
                    }

                }
            } else {
                Log.i(tag,"Start scan: SDK is not ready")
                function.onCallBack("Start scan: Cling SDK is not ready!")
            }
        }
        mWebView?.registerHandler("stopScan") { _, function ->
            Log.i(tag, "js call stop scan")
            if (sdkReady) {
                ClingSdk.stopScan()
                function.onCallBack("stop scan")
            } else {
                Log.i(tag,"Stop scan: SDK is not ready")
                function.onCallBack("Stop scan: Cling SDK is not ready!")
            }
        }
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
