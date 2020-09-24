/*
* iict devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* 主activity，其中要包含clingsdk的一些方法，继承自jsActivity，获得父类全部基础的webviewbridge注册方法
 */
package cn.iict.virustrack

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
import com.yc.pedometer.info.TemperatureInfo
import com.yc.pedometer.listener.TemperatureListener
import com.yc.pedometer.sdk.*
import com.yc.pedometer.utils.GlobalVariable
import wendu.webviewjavascriptbridge.WVJBWebView
import wendu.webviewjavascriptbridge.WVJBWebView.WVJBHandler

class MainActivity : WebViewActivity() {
    private var splashView: LinearLayout? = null
    private var mBLEServiceOperate: BLEServiceOperate? = null
    private var mBluetoothLeService: BluetoothLeService? = null
    private var mWriteCommand: WriteCommandToBLE? = null
    private var mDataProcessing: DataProcessing? = null
    private var connectStatus: Boolean = false
    private var connectDevice: BleDeviceData? = null

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

    // 手环的一系列回调
    private fun registerCallback(mWebView: WVJBWebView) {
        // 用于BluetoothLeService实例化准备,必须
        mBLEServiceOperate = BLEServiceOperate.getInstance(applicationContext)
        mWriteCommand = WriteCommandToBLE.getInstance(applicationContext)
        mDataProcessing = DataProcessing.getInstance(applicationContext)

        // 注册接收器，手环版本，电量这些
        val mFilter = IntentFilter()
        mFilter.addAction(GlobalVariable.READ_BATTERY_ACTION)
        mFilter.addAction(GlobalVariable.READ_BLE_VERSION_ACTION)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action == GlobalVariable.READ_BLE_VERSION_ACTION) {
                    val version = intent.getStringExtra(GlobalVariable.INTENT_BLE_VERSION_EXTRA)
                    Log.i(tag, "version: $version")
                    mWebView.callHandler("OnBandVersion", json(1, version, "get band version"))
                } else if ((action == GlobalVariable.READ_BATTERY_ACTION)) {
                    val battery = intent.getIntExtra(GlobalVariable.INTENT_BLE_BATTERY_EXTRA, -1)
                    Log.i(tag, "battery: $battery")
                    mWebView.callHandler("OnBandBattery", json(1, battery, "get band battery"))
                }
            }
        }, mFilter)

        // 注册服务操作
        mBLEServiceOperate?.let {
            // 扫描回调
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

                    // 体温回调
                    it.bleService.setTemperatureListener(object : TemperatureListener {
                        override fun onTestResult(p0: TemperatureInfo?) {
                            p0?.let { temp ->
                                val data = BandTemperatureData(
                                    temp.type,
                                    temp.calendar,
                                    temp.startDate,
                                    temp.secondTime,
                                    temp.bodyTemperature,
                                    temp.bodySurfaceTemperature,
                                    temp.ambientTemperature
                                )
                                mWebView.callHandler(
                                    "BandTestTemperature", json(1, data, "getTestTemperature")
                                )
                            }
                        }

                        override fun onSamplingResult(p0: TemperatureInfo?) {
                            p0?.let { temp ->
                                val data = BandTemperatureData(
                                    temp.type,
                                    temp.calendar,
                                    temp.startDate,
                                    temp.secondTime,
                                    temp.bodyTemperature,
                                    temp.bodySurfaceTemperature,
                                    temp.ambientTemperature
                                )
                                mWebView.callHandler(
                                    "BandSampleTemperature", json(1, data, "getSampleTemperature")
                                )
                            }
                        }
                    })
                    // icallback
                    it.bleService.setICallback(object : ICallback {

                        override fun OnResult(flag: Boolean, status: Int) {
                            Log.i("BandConnect", "OnResult $status")
                            // 连上
                            if (status == ICallbackStatus.CONNECTED_STATUS) {
                                Log.i(
                                    "BandConnect",
                                    "flag=$flag, connected, ${connectDevice?.name}"
                                )
                                connectStatus = true
                                // 连接成功返回手环信息
                                mWebView.callHandler(
                                    "OnBandConnected", json(1, connectDevice, "connected")
                                )
                            }
                            // 断开
                            if (status == ICallbackStatus.DISCONNECT_STATUS) {
                                Log.i(
                                    "BandConnect",
                                    "flag=$flag, disconnected, ${connectDevice?.name}"
                                )
                                connectStatus = false
                                connectDevice = null
                                mWebView.callHandler(
                                    "OnBandDisconnected", json(1, null, "disconnected")
                                )
                            }
                            // 时间同步
                            if (status == ICallbackStatus.SYNC_TIME_OK) {
                                Log.i(
                                    "BandConnect",
                                    "flag=$flag, sync time success, ${connectDevice?.name}"
                                )
                                mWebView.callHandler(
                                    "OnBandTimeSync", json(1, null, "time sync successfully")
                                )
                            }
                            // 计步同步
                            if (status == ICallbackStatus.OFFLINE_STEP_SYNC_OK) {
                                Log.i(
                                    "BandConnect",
                                    "flag=$flag, sync step success, ${connectDevice?.name}"
                                )
                                mWebView.callHandler(
                                    "OnBandStepSync", json(1, null, "step sync successfully")
                                )
                            }
                            // 计步同步
                            if (status == ICallbackStatus.OFFLINE_SLEEP_SYNC_OK) {
                                Log.i(
                                    "BandConnect",
                                    "flag=$flag, sync sleep success, ${connectDevice?.name}"
                                )
                                mWebView.callHandler(
                                    "OnBandSleepSync", json(1, null, "sleep sync successfully")
                                )
                            }
                            // 心率同步
                            if (status == ICallbackStatus.OFFLINE_RATE_SYNC_OK) {
                                mWebView.callHandler(
                                    "OnBandRateSync", json(1, null, "rate sync successfully")
                                )
                            }
                            // 血压同步
                            if (status == ICallbackStatus.OFFLINE_BLOOD_PRESSURE_SYNC_OK) {
                                mWebView.callHandler(
                                    "OnBandBloodPressureSync",
                                    json(1, null, "blood pressure sync successfully")
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

        // data processing
        mDataProcessing?.let { p ->
            // 计步
            p.setOnStepChangeListener { s ->
                s?.let { it ->
                    val data = BandStepData(
                        it.step,
                        it.distance,
                        it.calories,
                        it.runSteps,
                        it.runDistance,
                        it.runDurationTime,
                        it.walkSteps,
                        it.walkCalories,
                        it.walkDistance,
                        it.walkDurationTime
                    )
                    Log.i("BandConnect", data.toString())
                    mWebView.callHandler("OnBandStepChange", json(1, data, "Step data changed"))
                }
            }
            p.setOnSleepChangeListener {
                mWebView.callHandler("OnBandSleepChange", json(1, null, "Sleep data changed"))
            }
            p.setOnRateListener { p0, p1 ->
                val data = BandRateData(p0, p1)
                mWebView.callHandler("OnBandRateChange", json(1, data, "Rate data changed"))
            }
            p.setOnBloodPressureListener { p0, p1, p2 ->
                val data = BloodPressureData(p0, p1, p2)
                mWebView.callHandler(
                    "OnBandBloodPressureChange",
                    json(1, data, "Blood pressure data changed")
                )
            }
        }
    }

    // these bridge functions can only used in this activity, not global and general
    override fun initBridge(mWebView: WVJBWebView) {
        Log.i(tag, "InitBridge")
        super.initBridge(mWebView)
        Log.i(tag, "Register Band Callbacks")
        // 设置连接后各种手环回调
        registerCallback(mWebView)

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
                    json(0, null, "Already connected, please disconnect first")
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
                function.onResult(json(1, null, "disconnect"))
            }
        })
        mWebView.registerHandler("syncBandTime", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync band time")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.syncBLETime()
                Log.i("BandConnect", "sync time")
                function.onResult(json(1, null, "sync time"))
            }
        })

        // 获取手环版本
        mWebView.registerHandler("bandVersion", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band version")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.sendToReadBLEVersion()
                function.onResult(json(1, null, "broadcast version"))
            }
        })

        // 获取手环电量
        mWebView.registerHandler("bandBattery", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band battery")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.sendToReadBLEBattery()
                function.onResult(json(1, null, "broadcast battery"))
            }
        })

        // 获取体温
        mWebView.registerHandler("bodyTemperature", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band body temperature")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.queryCurrentTemperatureData()
                function.onResult(json(1, null, "test temperature"))
            }
        })

        // 同步计步
        mWebView.registerHandler("syncStep", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync step")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.syncAllStepData()
                function.onResult(json(1, null, "sync step data"))
            }
        })

        // 同步睡眠
        mWebView.registerHandler("syncSleep", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync sleep")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.syncAllSleepData()
                function.onResult(json(1, null, "sync sleep data"))
            }
        })

        // 同步心率
        mWebView.registerHandler("syncRate", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync rate")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.syncAllRateData()
                function.onResult(json(1, null, "sync rate data"))
            }
        })

        // 心率测试开关
        mWebView.registerHandler("testRate", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call test rate")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SwitchData::class.java)
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                if (data.flag!!) it.sendRateTestCommand(GlobalVariable.RATE_TEST_START)
                else it.sendRateTestCommand(GlobalVariable.RATE_TEST_STOP)
                function.onResult(json(1, null, "test rate data ${data.flag}"))
            }
        })

        // 同步血压
        mWebView.registerHandler("syncBloodPressure", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync blood pressure")
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                it.syncAllBloodPressureData()
                function.onResult(json(1, null, "sync blood pressure"))
            }
        })

        // 测试血压
        mWebView.registerHandler("testBloodPressure", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call test blood pressure")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SwitchData::class.java)
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                if (data.flag!!) it.sendBloodPressureTestCommand(GlobalVariable.BLOOD_PRESSURE_TEST_START)
                else it.sendBloodPressureTestCommand(GlobalVariable.BLOOD_PRESSURE_TEST_STOP)
                function.onResult(json(1, null, "test blood pressure ${data.flag}"))
            }
        })

        // 检查采集体温开关
        mWebView.registerHandler("temperatureStatus", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call set/get temp status")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SwitchData::class.java)
            if (!connectStatus || connectDevice == null)
                return@WVJBHandler function.onResult(json(0, null, "no band connected"))
            mWriteCommand?.let {
                if (data.flag == null) { //null时查询
                    Log.i(tag, "get temp status")
                    it.queryRawTemperatureStatus()
                    function.onResult(json(1, null, "query raw temperature status"))
                } else {
                    Log.i(tag, "set temp status")
                    it.setRawTemperatureStatus(data.flag)
                    function.onResult(json(1, null, "set raw temperature status, ${data.flag}"))
                }
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
