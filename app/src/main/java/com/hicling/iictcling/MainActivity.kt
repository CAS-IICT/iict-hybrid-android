package com.hicling.iictcling

import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import com.github.lzyzsd.jsbridge.BridgeWebView
import com.hicling.clingsdk.ClingSdk
import com.hicling.clingsdk.devicemodel.PERIPHERAL_DEVICE_INFO_CONTEXT
import com.hicling.clingsdk.listener.OnBleListener.OnBleDataListener
import com.hicling.clingsdk.listener.OnBleListener.OnDeviceConnectedListener
import com.hicling.clingsdk.listener.OnSdkReadyListener
import com.hicling.clingsdk.model.DayTotalDataModel


class MainActivity : JsActivity() {
    private val url: String = "http://192.168.1.79:8080"
    private val appID: String = "HCd176b8b47b3ed84c"
    private val appSecret: String = "92f767d18c3e843bb23e317617c55175"
    private val tag = this.javaClass.canonicalName
    private var deviceInfo: PERIPHERAL_DEVICE_INFO_CONTEXT? = null


    // sdk is ready
    private val clingReady = object : OnSdkReadyListener {
        override fun onClingSdkReady() {
            Log.i(tag, "onClingSdkReady")
        }

        override fun onFailed(s: String?) {
            Log.i(tag, "onClingSdkReady onFailed $s")
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
