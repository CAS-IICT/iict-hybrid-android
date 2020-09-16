/*
* iict devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* 主activity，其中要包含clingsdk的一些方法，继承自jsActivity，获得父类全部基础的webviewbridge注册方法
 */
package com.hicling.iictcling

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.google.gson.Gson
import com.yc.pedometer.info.HeartRateHeadsetSportModeInfo
import com.yc.pedometer.info.SportsModesInfo
import com.yc.pedometer.sdk.BLEServiceOperate
import com.yc.pedometer.sdk.BluetoothLeService
import com.yc.pedometer.sdk.ICallback
import com.yc.pedometer.sdk.ICallbackStatus
import wendu.webviewjavascriptbridge.WVJBWebView
import wendu.webviewjavascriptbridge.WVJBWebView.WVJBHandler

class MainActivity : WebViewActivity() {
    private var splashView: LinearLayout? = null
    private var mBLEServiceOperate: BLEServiceOperate? = null
    private var mBluetoothLeService: BluetoothLeService? = null

    private val content = R.layout.activity_main
    override val tag = this.javaClass.simpleName
    private val handler = Handler()

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

    // these bridge functions can only used in this activity, not global and general
    override fun initBridge(mWebView: WVJBWebView) {
        super.initBridge(mWebView)
        Log.i(tag, "MainActivity: initBridge")
        // 用于BluetoothLeService实例化准备,必须
        mBLEServiceOperate = BLEServiceOperate.getInstance(applicationContext)
        // 设置扫描后回调，服务开启后回调
        mBLEServiceOperate?.let {
            it.setDeviceScanListener { device, rssi, _ ->
                val returnData = getBleDeviceData(device, rssi)
                returnData?.let { it2 ->
                    mWebView.callHandler(
                        "BandOnScanResult",
                        json(1, it2, "Scan Band device: ${device.name}")
                    )
                }
            }
            it.setServiceStatusCallback { status ->
                if (status == ICallbackStatus.BLE_SERVICE_START_OK) {
                    mBluetoothLeService = it.bleService
                    it.bleService.setICallback(object : ICallback {
                        override fun OnDataResult(flag: Boolean, status: Int, data: ByteArray?) {
                            if (data != null && data.isNotEmpty()) {
                                val stringBuilder = StringBuilder(data.size)
                                for (byteChar in data) {
                                    stringBuilder.append(String.format("%02X", byteChar))
                                }
                                Log.i("BandConnect", "BLE---->APK data =$stringBuilder")
                            }
                        }

                        override fun onSportsTimeCallback(
                            p0: Boolean,
                            p1: String?,
                            p2: Int,
                            p3: Int
                        ) {
                            Log.i("BandConnect", "onSportsTimeCallback")
                        }

                        override fun OnResultSportsModes(
                            p0: Boolean,
                            p1: Int,
                            p2: Boolean,
                            p3: Int,
                            p4: SportsModesInfo?
                        ) {
                            Log.i("BandConnect", "onResultSportsModes")
                        }

                        override fun OnResultHeartRateHeadset(
                            p0: Boolean,
                            p1: Int,
                            p2: Int,
                            p3: Int,
                            p4: HeartRateHeadsetSportModeInfo?
                        ) {
                            Log.i("BandConnect", "OnResultHeartRateHeadset")
                        }

                        override fun OnResult(flag: Boolean, status: Int) {
                            Log.i("BandConnect", "OnResult")
                            if (status == ICallbackStatus.CONNECTED_STATUS) {
                                Log.i("BandConnect", "flag=$flag, connected")
                                mWebView.callHandler(
                                    "OnBandDisconnected", json(1, null, "Finish Scan")
                                )
                            }
                            if (status == ICallbackStatus.DISCONNECT_STATUS) {
                                Log.i("BandConnect", "flag=$flag, disconnected")
                                mWebView.callHandler(
                                    "OnBandConnected", json(1, null, "Finish Scan")
                                )
                            }
                        }

                        override fun onCharacteristicWriteCallback(p0: Int) {
                            Log.i("BandConnect", "onCharacteristicWriteCallback")
                        }

                        override fun onIbeaconWriteCallback(
                            p0: Boolean,
                            p1: Int,
                            p2: Int,
                            p3: String?
                        ) {
                            Log.i("BandConnect", "onIbeaconWriteCallback")
                        }

                        override fun onQueryDialModeCallback(
                            p0: Boolean,
                            p1: Int,
                            p2: Int,
                            p3: Int
                        ) {
                            Log.i("BandConnect", "onQueryDialModeCallback")
                        }

                        override fun onControlDialCallback(p0: Boolean, p1: Int, p2: Int) {
                            Log.i("BandConnect", "onControlDialCallback")
                        }
                    })
                }
            }
        }

        // 设置连接后各种手环回调

        // 扫描手环
        mWebView.registerHandler("scanBand", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call scan band")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), ScanBleData::class.java)

            mBLEServiceOperate?.let {
                if (it.isSupportBle4_0 && it.isBleEnabled) {
                    Log.i(tag, "start scan band")
                    it.stopLeScan() //先关闭原来的扫描，不重复扫描
                    it.startLeScan()
                    // 定时关闭蓝牙扫描
                    handler.postDelayed({
                        Log.i(tag, "stop scan band")
                        it.stopLeScan()
                        mWebView.callHandler(
                            "BandFinishScan", json(1, null, "Finish Scan")
                        )
                    }, data.time)

                    function.onResult(json(1, null, "start scan the bands"))
                } else {
                    function.onResult(json(0, null, "device not support ble 4.0"))
                }
            }
        })

        // 连接手环
        mWebView.registerHandler("connectBand", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call connect band")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BleDeviceData::class.java)
            mBLEServiceOperate?.let {
                it.connect(data.mac)
                Log.i("BandConnect", "Start to connect")
            }
            function.onResult(json(1, null, "connect"))
        })

        //断开连接
        mWebView.registerHandler("disConnectBand", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call disconnect band")
            mBLEServiceOperate?.let {
                it.disConnect()
                Log.i("BandConnect", "Start to disconnect")
            }
            function.onResult(json(1, null, "disconnect"))
        })
    }

    override fun onLoadFinish() {
        super.onLoadFinish()
        splashView?.let {
            Animation().fadeOut(it as View, 1000)
        }
    }

    override fun onLoadError() {
        super.onLoadError()
        splashView?.let {
            Animation().fadeOut(it as View, 1000)
        }
    }

    override fun onDestroy() {
        Log.i(tag, "onDestroy()")
        mBLEServiceOperate?.stopLeScan()
        mBLEServiceOperate?.unBindService() // unBindService
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
