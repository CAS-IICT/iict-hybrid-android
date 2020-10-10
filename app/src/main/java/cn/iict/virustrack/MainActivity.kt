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
import wendu.webviewjavascriptbridge.WVJBWebView.WVJBResponseCallback

class MainActivity : WebViewActivity() {
    private var splashView: LinearLayout? = null

    // 接下来5个是UTE手环常用的操作对象
    private var mBLEServiceOperate: BLEServiceOperate? = null
    private var mBluetoothLeService: BluetoothLeService? = null
    private var mSQLOperate: UTESQLOperate? = null
    private var mWriteCommand: WriteCommandToBLE? = null
    private var mDataProcessing: DataProcessing? = null

    private var connectStatus: Boolean = false
    private var connectDevice: BleDeviceData? = null

    override val content = R.layout.activity_main
    override val tag = this.javaClass.simpleName
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mWebView?.let {
            splashView = findViewById(R.id.splash)
            initBridge(it)
        }
    }

    // 手环的一系列回调
    private fun registerCallback(mWebView: WVJBWebView) {
        // 用于BluetoothLeService实例化准备,必须
        mBLEServiceOperate = BLEServiceOperate.getInstance(applicationContext)
        mWriteCommand = WriteCommandToBLE.getInstance(applicationContext)
        mDataProcessing = DataProcessing.getInstance(applicationContext)
        mSQLOperate = UTESQLOperate.getInstance(applicationContext)

        // 注册接收器，手环版本，电量这些
        val mFilter = IntentFilter()
        mFilter.addAction(GlobalVariable.READ_BATTERY_ACTION)
        mFilter.addAction(GlobalVariable.READ_BLE_VERSION_ACTION)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mWebView.callHandler("BandUnlock")
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
                        "OnBandScanResult",
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
                            mWebView.callHandler("BandUnlock")
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
                            mWebView.callHandler("BandUnlock")
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
                            Log.i("OnResult", "$flag $status")
                            if (status == ICallbackStatus.CONNECTED_STATUS) {
                                mWebView.callHandler("BandUnlock")
                                connectStatus = true
                                // 连接成功返回手环信息
                                mWebView.callHandler(
                                    "OnBandConnected", json(1, connectDevice, "connected")
                                )
                            }
                            // connect timeout
                            if (status == ICallbackStatus.BLE_CONNECT_TIMEOUT) {
                                mWebView.callHandler("BandUnlock")
                                mWebView.callHandler(
                                    "OnBandConnectTimeout",
                                    json(0, connectDevice, "connect device timeout")
                                )
                                connectStatus = false
                            }
                            // 断开
                            if (status == ICallbackStatus.DISCONNECT_STATUS) {
                                mWebView.callHandler("BandUnlock")
                                connectStatus = false
                                connectDevice = null
                                mWebView.callHandler(
                                    "OnBandDisconnected", json(1, null, "disconnected")
                                )
                            }
                            // 时间同步
                            if (status == ICallbackStatus.SYNC_TIME_OK) {
                                mWebView.callHandler("BandUnlock")
                                mWebView.callHandler(
                                    "OnBandTimeSync", json(1, null, "time sync successfully")
                                )
                            }
                            // 计步同步
                            if (status == ICallbackStatus.OFFLINE_STEP_SYNC_OK) {
                                mWebView.callHandler("BandUnlock")
                                mWebView.callHandler(
                                    "OnBandStepSync",
                                    json(1, null, "step sync successfully")
                                )
                            }
                            if (status == ICallbackStatus.OFFLINE_STEP_SYNC_TIMEOUT) {
                                mWebView.callHandler("BandUnlock")
                            }
                            // 计步同步
                            if (status == ICallbackStatus.OFFLINE_SLEEP_SYNC_OK) {
                                mWebView.callHandler("BandUnlock")
                                mWebView.callHandler(
                                    "OnBandSleepSync", json(1, null, "sleep sync successfully")
                                )
                            }
                            if (status == ICallbackStatus.OFFLINE_SLEEP_SYNC_TIMEOUT) {
                                mWebView.callHandler("BandUnlock")
                            }
                            // 心率同步
                            if (status == ICallbackStatus.OFFLINE_RATE_SYNC_OK) {
                                mWebView.callHandler("BandUnlock")
                                mWebView.callHandler(
                                    "OnBandRateSync", json(1, null, "rate sync successfully")
                                )
                            }
                            if (status == ICallbackStatus.OFFLINE_RATE_SYNC_TIMEOUT) {
                                mWebView.callHandler("BandUnlock")
                            }
                            // 血压同步
                            if (status == ICallbackStatus.OFFLINE_BLOOD_PRESSURE_SYNC_OK) {
                                mWebView.callHandler("BandUnlock")
                                mWebView.callHandler(
                                    "OnBandBloodPressureSync",
                                    json(1, null, "blood pressure sync successfully")
                                )
                            }
                            if (status == ICallbackStatus.OFFLINE_BLOOD_PRESSURE_SYNC_TIMEOUT) {
                                mWebView.callHandler("BandUnlock")
                            }
                            if (status == ICallbackStatus.SYNC_TEMPERATURE_COMMAND_OK) {
                                mWebView.callHandler(
                                    "OnBandTemperatureSync",
                                    json(1, null, "temperature data sync successfully")
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
                mWebView.callHandler("BandUnlock")
                mWebView.callHandler("OnBandSleepChange", json(1, null, "Sleep data changed"))
            }
            p.setOnRateListener { p0, p1 ->
                mWebView.callHandler("BandUnlock")
                val data = BandRateData(p0, p1)
                mWebView.callHandler("OnBandRateChange", json(1, data, "Rate data changed"))
            }
            p.setOnBloodPressureListener { p0, p1, p2 ->
                mWebView.callHandler("BandUnlock")
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
            if (check(function)) {
                mBLEServiceOperate?.let {
                    if (!it.isSupportBle4_0) return@WVJBHandler function.onResult(
                        json(0, null, "ble 4.0 is not support")
                    )
                    Log.i(tag, "start scan band")
                    it.stopLeScan() //先关闭原来的扫描，不重复扫描
                    it.startLeScan()
                    // 定时关闭蓝牙扫描
                    handler.postDelayed({
                        Log.i(tag, "stop scan band")
                        it.stopLeScan()
                        mWebView.callHandler("OnBandScanFinish", json(1, null, "Finish Scan"))
                    }, data.time)

                    function.onResult(json(1, null, "start scan the bands"))
                }
            }
        })

        // 连接手环
        mWebView.registerHandler("connectBand", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call connect band")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BleDeviceData::class.java)
            if (check(function)) {
                mBLEServiceOperate?.let {
                    mWebView.callHandler("BandLock")
                    // 已经是连接状态，禁止再连接
                    if (connectStatus && connectDevice != null) {
                        mWebView.callHandler("BandUnlock")
                        function.onResult(json(0, null, "Already connected, disconnect first"))
                    } else {
                        // reset connect status
                        connectDevice = data
                        connectStatus = false
                        it.connect(data.mac)
                        function.onResult(json(1, null, "connecting"))
                    }
                }
            }
        })

        // 检查已连接的手环
        mWebView.registerHandler("checkBand", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call check band")
            if (check(function)) {
                if (connectStatus && connectDevice != null)
                    function.onResult(json(1, connectDevice, "connected"))
                else {
                    connectDevice = null
                    function.onResult(json(0, null, "unconnected"))
                }
            }
        })

        // 断开连接
        mWebView.registerHandler("disConnectBand", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call disconnect band")
            if (check(function)) {
                mBLEServiceOperate?.let {
                    mWebView.callHandler("BandLock")
                    it.disConnect()
                    Log.i("BandConnect", "Start to disconnect")
                    function.onResult(json(1, null, "disconnect"))
                }
            }
        })
        mWebView.registerHandler("syncBandTime", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync band time")
            if (check(function, true)) {
                mWriteCommand?.let {
                    Log.i("BandConnect", "sync time")
                    mWebView.callHandler("BandLock")
                    it.syncBLETime()
                    function.onResult(json(1, null, "sync time"))
                }
            }
        })

        // 获取手环版本
        mWebView.registerHandler("bandVersion", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band version")
            if (check(function, true)) {
                mWriteCommand?.let {
                    mWebView.callHandler("BandLock")
                    it.sendToReadBLEVersion()
                    function.onResult(json(1, null, "broadcast version"))
                }
            }
        })

        // 获取手环电量
        mWebView.registerHandler("bandBattery", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band battery")
            if (check(function, true)) {
                mWriteCommand?.let {
                    mWebView.callHandler("BandLock")
                    it.sendToReadBLEBattery()
                    function.onResult(json(1, null, "broadcast battery"))
                }
            }
        })

        // 获取体温
        mWebView.registerHandler("bodyTemperature", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call band body temperature")
            if (check(function, true)) {
                mWriteCommand?.let {
                    mWebView.callHandler("BandLock")
                    it.queryCurrentTemperatureData()
                    function.onResult(json(1, null, "test temperature"))
                }
            }
        })

        // 同步计步
        mWebView.registerHandler("syncStep", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync step")
            if (check(function, true)) {
                mWriteCommand?.let {
                    mWebView.callHandler("BandLock")
                    it.syncAllStepData()
                    function.onResult(json(1, null, "sync step data"))
                }
            }
        })

        // 同步睡眠
        mWebView.registerHandler("syncSleep", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync sleep")
            if (check(function, true)) {
                mWriteCommand?.let {
                    mWebView.callHandler("BandLock")
                    it.syncAllSleepData()
                    function.onResult(json(1, null, "sync sleep data"))
                }
            }
        })

        // 同步心率
        mWebView.registerHandler("syncRate", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync rate")
            if (check(function, true)) {
                mWriteCommand?.let {
                    mWebView.callHandler("BandLock")
                    it.syncAllRateData()
                    function.onResult(json(1, null, "sync rate data"))
                }
            }
        })

        // 心率测试开关
        mWebView.registerHandler("testRate", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call test rate")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SwitchData::class.java)
            if (check(function, true)) {
                mWriteCommand?.let {
                    mWebView.callHandler("BandLock")
                    if (data.flag!!) it.sendRateTestCommand(GlobalVariable.RATE_TEST_START)
                    else it.sendRateTestCommand(GlobalVariable.RATE_TEST_STOP)
                    function.onResult(json(1, null, "test rate data ${data.flag}"))
                }
            }
        })

        // 同步血压
        mWebView.registerHandler("syncBloodPressure", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync blood pressure")
            if (check(function, true)) {
                mWriteCommand?.let {
                    it.syncAllBloodPressureData()
                    function.onResult(json(1, null, "sync blood pressure"))
                }
            }
        })

        // 测试血压
        mWebView.registerHandler("testBloodPressure", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call test blood pressure")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SwitchData::class.java)
            if (check(function, true)) {
                mWriteCommand?.let {
                    if (data.flag!!) it.sendBloodPressureTestCommand(GlobalVariable.BLOOD_PRESSURE_TEST_START)
                    else it.sendBloodPressureTestCommand(GlobalVariable.BLOOD_PRESSURE_TEST_STOP)
                    function.onResult(json(1, null, "test blood pressure ${data.flag}"))
                }
            }
        })
        // 同步体温
        mWebView.registerHandler("syncTemperature", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call sync temperature")
            if (check(function, true)) {
                mWriteCommand?.let {
                    it.syncAllTemperatureData()
                    function.onResult(json(1, null, "sync all temperature data"))
                }
            }
        })

        // 检查采集体温开关
        mWebView.registerHandler("temperatureStatus", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call set/get temp status")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), SwitchData::class.java)
            if (check(function, true)) {
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
            }
        })

        // 各种数据库读写
        mWebView.registerHandler("queryStepDate", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call query step date")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BandDateData::class.java)
            if (check(function, true)) {
                // 当日步数
                val data = mSQLOperate?.queryStepDate(data.date)
                function.onResult(json(1, data, "one day step"))
            }
        })
        mWebView.registerHandler("queryStepInfo", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call query step info")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BandDateData::class.java)
            if (check(function, true)) {
                mSQLOperate?.let {
                    val i = it.queryStepInfo(data.date)
                    if (i != null) {
                        // 步数详细
                        val data = BandStepData(i.step, i.distance, i.calories)
                        function.onResult(json(1, data, "one day step"))
                    } else function.onResult(json(0, null, "no one day step"))
                }
            }
        })
        // 返回睡眠时间int
        mWebView.registerHandler("querySleepDate", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call query sleep date")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BandDateData::class.java)
            if (check(function, true)) {
                // 返回睡眠时间，int，单位是分钟
                val data = mSQLOperate?.querySleepDate(data.date)
                function.onResult(json(1, data, "one day sleep"))
            }
        })
        // 返回详细睡眠信息
        mWebView.registerHandler("querySleepInfo", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call query sleep info")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BandDateData::class.java)
            if (check(function, true)) {
                mSQLOperate?.let {
                    val i = it.querySleepInfo(data.date)
                    if (i != null) {
                        // 步数详细
                        val data = BandSleepData(
                            i.sleepTotalTime,
                            i.deepTime,
                            i.lightTime,
                            i.awakeTime,
                            i.awakeCount,
                            i.beginTime,
                            i.endTime,
                            i.sleepStatueArray,
                            i.durationTimeArray,
                            i.timePointArray
                        )
                        function.onResult(json(1, data, "sleep info"))
                    } else function.onResult(json(0, null, "no sleep info"))
                }
            }
        })
        // 返回某一天最高最低当前平均心率
        mWebView.registerHandler("queryRateDate", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call query rate date")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BandDateData::class.java)
            if (check(function, true)) {
                mSQLOperate?.let {
                    val i = it.queryRateOneDayMainInfo(data.date)
                    if (i != null) {
                        val data =
                            BandOneDayRateData(
                                i.currentRate,
                                i.lowestRate,
                                i.verageRate,
                                i.highestRate
                            )
                        function.onResult(json(1, data, "rate date"))
                    } else function.onResult(json(0, null, "no rate data"))
                }
            }
        })
        // 返回某一天最高最低当前平均心率
        mWebView.registerHandler("queryRateInfo", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call query rate info")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BandDateData::class.java)
            if (check(function, true)) {
                mSQLOperate?.let {
                    val i = it.queryRateOneDayDetailInfo(data.date)
                    val data = HashMap<String, BandOneDayRateData>()
                    for (v in i) {
                        data[v.time.toString()] = BandOneDayRateData(
                            v.currentRate,
                            v.lowestRate,
                            v.verageRate,
                            v.highestRate
                        )
                    }
                    function.onResult(json(1, data, "rate info"))
                }
            }
        })
        // 返回某一天血压
        mWebView.registerHandler(
            "queryBloodPressureDate",
            WVJBHandler<Any?, Any?> { data, function ->
                Log.i(tag, "js call query sleep info")
                Log.i(tag, data.toString())
                val data = Gson().fromJson(data.toString(), BandDateData::class.java)
                if (check(function, true)) {
                    mSQLOperate?.let {
                        val i = it.queryBloodPressureOneDayInfo(data.date)
                        val data = ArrayList<BloodPressureData>()
                        for (v in i) {
                            Log.i("blood",v.bloodPressureTime.toString())
                            data.add(BloodPressureData(v.hightBloodPressure, v.lowBloodPressure, 0))
                        }
                        function.onResult(json(1, data, "blood pressure date data"))
                    }
                }
            })
        // 返回某一天体温
        mWebView.registerHandler("queryTemperatureDate", WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call query temperature date")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), BandDateData::class.java)
            if (check(function, true)) {
                mSQLOperate?.let {
                    val i = it.queryTemperatureDate(data.date)
                    val data = ArrayList<TemperatureInfoData>()
                    for (v in i) {
                        data.add(
                            TemperatureInfoData(
                                v.calendar,
                                v.startDate,
                                v.secondTime,
                                v.bodySurfaceTemperature,
                                v.bodyTemperature,
                                v.ambientTemperature,
                                v.type
                            )
                        )
                    }
                    function.onResult(json(1, data, "query temperature date data"))
                }
            }
        })
        // 返回全部体温
        mWebView.registerHandler("queryTemperatureInfo", WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call query temperature info all data")
            if (check(function, true)) {
                mSQLOperate?.let {
                    val i = it.queryTemperatureAll()
                    val data = ArrayList<TemperatureInfoData>()
                    for (v in i) {
                        data.add(
                            TemperatureInfoData(
                                v.calendar,
                                v.startDate,
                                v.secondTime,
                                v.bodySurfaceTemperature,
                                v.bodyTemperature,
                                v.ambientTemperature,
                                v.type
                            )
                        )
                    }
                    function.onResult(json(1, data, "query temperature all data"))
                }
            }
        })
    }

    // 检查蓝牙状态是否完备
    private fun check(function: WVJBResponseCallback<Any>, band: Boolean = false): Boolean {
        // 检查蓝牙
        if (!checkBle()) {
            function.onResult(json(0, null, "ble service is not ready"))
            return false
        }
        // 检查sdk
        if (mBLEServiceOperate == null || mWriteCommand == null || mSQLOperate == null || mBluetoothLeService == null) {
            function.onResult(json(0, null, "band sdk is not ready"))
            return false
        }
        Log.i("band", band.toString())
        // 检查连接手环
        if (band && (!connectStatus || connectDevice == null)) {
            function.onResult(json(0, null, "band is not connected"))
            return false
        }
        // 通过
        return true
    }

    override fun onLoadFinish() {
        super.onLoadFinish()
        splashView?.let {
            Animation.fadeOut(it as View, 1000)
        }
    }

    override fun onLoadError() {
        super.onLoadError()
        splashView?.let {
            Animation.fadeOut(it as View, 1000)
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
