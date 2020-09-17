/*
* iict devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* 主activity，其中要包含clingsdk的一些方法，继承自jsActivity，获得父类全部基础的webviewbridge注册方法
 */
package com.hicling.iictcling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.google.gson.Gson
import com.yc.pedometer.info.HeartRateHeadsetSportModeInfo
import com.yc.pedometer.info.SportsModesInfo
import com.yc.pedometer.sdk.*
import com.yc.pedometer.utils.GlobalVariable
import wendu.webviewjavascriptbridge.WVJBWebView
import wendu.webviewjavascriptbridge.WVJBWebView.WVJBHandler

class MainActivity : WebViewActivity() {
    private var splashView: LinearLayout? = null
    private var mBLEServiceOperate: BLEServiceOperate? = null
    private var mBluetoothLeService: BluetoothLeService? = null
    private var connectStatus: Boolean = false
    private var connectDevice: BleDeviceData? = null
    private var mWriteCommand: WriteCommandToBLE? = null

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

    private fun registerReceiver() {
        val mFilter = IntentFilter()
        mFilter.addAction(GlobalVariable.READ_BATTERY_ACTION)
        mFilter.addAction(GlobalVariable.READ_BLE_VERSION_ACTION)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action == GlobalVariable.READ_BLE_VERSION_ACTION) {
                    val version = intent.getStringExtra(GlobalVariable.INTENT_BLE_VERSION_EXTRA)
                    Log.i(tag, "version: $version")
                    mWebView?.callHandler("BandOnVersion", json(1, version, "get band version"))
                } else if ((action == GlobalVariable.READ_BATTERY_ACTION)) {
                    val battery = intent.getIntExtra(GlobalVariable.INTENT_BLE_BATTERY_EXTRA, -1)
                    Log.i(tag, "battery: $battery")
                    mWebView?.callHandler("BandOnBattery", json(1, battery, "get band battery"))
                }
            }
        }, mFilter)
    }

    private fun registerBLECallback(mWebView: WVJBWebView) {
        // 用于BluetoothLeService实例化准备,必须
        mBLEServiceOperate = BLEServiceOperate.getInstance(applicationContext)
        mWriteCommand = WriteCommandToBLE.getInstance(applicationContext)
        // 设置扫描后回调，服务开启后回调
        mBLEServiceOperate?.let {
            it.setDeviceScanListener { device, rssi, _ ->
                Log.i("bandScan", "find device ${device.name}")
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

                        override fun OnResult(flag: Boolean, status: Int) {
                            Log.i("BandConnect", "OnResult $status")
                            // 连上
                            if (status == ICallbackStatus.CONNECTED_STATUS) {
                                Log.i("BandConnect", "flag=$flag, connected")
                                connectStatus = true
                                mWebView.callHandler(
                                    "OnBandDisconnected", json(1, null, "connected")
                                )
                            }
                            // 断开
                            if (status == ICallbackStatus.DISCONNECT_STATUS) {
                                Log.i("BandConnect", "flag=$flag, disconnected")
                                connectStatus = false
                                connectDevice = null
                                mWebView.callHandler(
                                    "OnBandConnected", json(1, null, "disconnected")
                                )
                            }
                        }

                        override fun OnDataResult(flag: Boolean, status: Int, data: ByteArray?) {
                            Log.i("BandConnect", "OnDataResult")
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

                        override fun onCharacteristicWriteCallback(p0: Int) {
                            Log.i("BandConnect", "onCharacteristicWriteCallback $p0")
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
    }

    // these bridge functions can only used in this activity, not global and general
    override fun initBridge(mWebView: WVJBWebView) {
        super.initBridge(mWebView)
        Log.i(tag, "MainActivity: initBridge")

        // 设置连接后各种手环回调
        registerBLECallback(mWebView)
        registerReceiver()

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
                // 已经是连接状态，禁止再连接
                if (connectStatus) return@WVJBHandler function.onResult(
                    json(0, null, "connected, please disconnect first")
                )
                // reset connect status
                connectDevice = data
                connectStatus = false
                it.connect(data.mac)
                Log.i("BandConnect", "Start to connect")
                return@WVJBHandler function.onResult(json(1, null, "connecting"))
            }
            return@WVJBHandler function.onResult(json(0, null, "BLEServiceOperate is null"))
        })

        // 检查已连接的手环
        mWebView.registerHandler("checkBand", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call check band")
            Log.i(tag, "$connectStatus $connectDevice")
            if (connectStatus) function.onResult(json(1, connectDevice, "connected"))
            else {
                connectDevice = null
                function.onResult(json(0, null, "unconnected"))
            }
        })

        // 断开连接
        mWebView.registerHandler("disConnectBand", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call disconnect band")
            mBLEServiceOperate?.let {
                it.disConnect()
                Log.i("BandConnect", "Start to disconnect")
            }
            function.onResult(json(1, null, "disconnect"))
        })

        // 获取手环版本
        mWebView.registerHandler("bandVersion", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band version")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(
                    json(0, null, "no band connected")
                )
            mWriteCommand?.let {
                it.sendToReadBLEVersion()
                function.onResult(json(1, null, "broadcast version"))
            }
        })

        // 获取手环版本
        mWebView.registerHandler("bandBattery", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band battery")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(
                    json(0, null, "no band connected")
                )
            mWriteCommand?.let {
                it.sendToReadBLEBattery()
                function.onResult(json(1, null, "broadcast battery"))
            }
        })
        // 获取手环版本
        mWebView.registerHandler("bandBattery", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band battery")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(
                    json(0, null, "no band connected")
                )
            mWriteCommand?.let {
                it.readAirPressureTemperatureHistory()
            }
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
