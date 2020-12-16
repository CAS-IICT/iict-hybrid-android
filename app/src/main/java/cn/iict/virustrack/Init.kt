package cn.iict.virustrack

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import cn.ac.iict.webviewjsbridgex5.WVJBWebView
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery
import com.google.gson.Gson
import com.hailong.appupdate.AppUpdateManager
import com.linchaolong.android.imagepicker.ImagePicker
import com.linchaolong.android.imagepicker.cropper.CropImage
import com.linchaolong.android.imagepicker.cropper.CropImageView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

object Init {
    val tag: String = this.javaClass.simpleName

    fun initBridge(mWebView: WVJBWebView, activity: WebViewActivity) {
        Log.i(tag, "WebViewActivity: initBridge")
        // bacn
        mWebView.registerHandler("back", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call back")
            if (mWebView.canGoBack()) {
                mWebView.goBack()
                function.onResult(activity.json(1))
            } else {
                function.onResult(activity.json(1))
                activity.finish()
            }
        })
        // toast
        mWebView.registerHandler("toast", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call toast")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), ToastData::class.java)
            activity.toast(data.text)
            function.onResult(activity.json(1))
        })
        // alert
        mWebView.registerHandler("alert", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call alert")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), AlertData::class.java)
            AlertDialog.Builder(activity)
                .setMessage(data.message)
                .setTitle(data.title)
                .setPositiveButton(data.btnConfirm) { _, _ ->
                    function.onResult(activity.json(1))
                }.create().show()
        })
        // loading
        mWebView.registerHandler("loading", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call loading")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), LoadingData::class.java)
            activity.showLoading(data.load)
            function.onResult(activity.json(1))
        })
        // set status bar
        mWebView.registerHandler(
            "setStatusBar",
            WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
                Log.i(tag, "js call set status bar")
                Log.i(tag, data.toString())
                val resources: Resources = activity.applicationContext.resources
                val resourceId: Int =
                    resources.getIdentifier("status_bar_height", "dimen", "android")
                val height = resources.getDimensionPixelSize(resourceId)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val data = Gson().fromJson(data.toString(), StatusBarData::class.java)
                    // set background
                    data.background?.let {
                        val color = Color.parseColor(data.background)
                        activity.window.statusBarColor = color
                    }
                    data.color?.let {
                        val decor: View = activity.window.decorView
                        if (data.color == "dark") {
                            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        } else {
                            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        }
                    }
                    function.onResult(
                        activity.json(
                            1,
                            height,
                            "set status bar successfully, color:${data.color}, bg:${data.background}"
                        )
                    )
                } else {
                    Log.i(tag, "call set status bar fail")
                    function.onResult(
                        activity.json(
                            0,
                            height,
                            "Low Version, need more than Android 5.0"
                        )
                    )
                }
            })
        // start a new webview activity and go to the specified url
        mWebView.registerHandler("go", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call go other webview, new activity")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), WebViewData::class.java)
            activity.goWebView(data.url, data.loading)
            function.onResult(activity.json(1, null, data.url))
        })
        // 获取手机屏幕大小
        mWebView.registerHandler("getWinSize", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call get win size")
            val manager = activity.windowManager
            val outMetrics = DisplayMetrics()
            manager.defaultDisplay.getMetrics(outMetrics)
            val width = outMetrics.widthPixels
            val height = outMetrics.heightPixels
            val data = WinSizeData(width, height)
            function.onResult(activity.json(1, data, "get window size"))
        })
        // get bottom navbar info
        mWebView.registerHandler(
            "getNavBarSize",
            WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
                Log.i(tag, "js call get nav bar size")
                val ctx = activity.applicationContext
                function.onResult(
                    activity.json(
                        1,
                        NavBarData(
                            NavUtil.getNavigationBarHeight(ctx),
                            NavUtil.hasNavigationBar(ctx),
                            NavUtil.hasBottomIndicator(ctx)
                        ),
                        "get nav bar size"
                    )
                )
            })
        // get local uuid
        mWebView.registerHandler(
            "getLocalUuid",
            WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
                Log.i(tag, "js call get local uuid")
                val uuids = activity.getLocalUUID()
                function.onResult(activity.json(1, uuids, "local uuids"))
            })
        // check bluetooth device status
        mWebView.registerHandler("checkBle", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call checkBle")
            if (activity.checkBle()) {
                val msg = "Bluetooth is enabled"
                Log.i(tag, msg)
                function.onResult(activity.json(1, null, msg))
            } else {
                val msg = "Bluetooth is unavailable"
                Log.i(tag, msg)
                function.onResult(activity.json(0, null, msg))
            }
        })

        // turn on bluetooth device
        mWebView.registerHandler("turnOnBle", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call turn on Ble")
            val blueAdapter = BluetoothAdapter.getDefaultAdapter()
            if (blueAdapter != null) {
                if (blueAdapter.isEnabled)
                    function.onResult(activity.json(1, null, "Ble is on"))
                else {
                    blueAdapter.enable()
                    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                    val bleOpenCallback = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent) {
                            val action = intent.action
                            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                                val action = intent.getIntExtra(
                                    BluetoothAdapter.EXTRA_STATE,
                                    BluetoothAdapter.ERROR
                                )
                                when (action) {
                                    BluetoothAdapter.STATE_OFF -> function.onResult(
                                        activity.json(0, null, "Ble is off")
                                    )
                                    BluetoothAdapter.STATE_ON -> function.onResult(
                                        activity.json(1, null, "Ble is on")
                                    )
                                }
                            }
                        }
                    }
                    activity.registerReceiver(bleOpenCallback, filter)
                }
            } else function.onResult(activity.json(0, null, "Fail to turn on, no ble adapter"))
        })


        // start BLE GATT service
        mWebView.registerHandler("setGATT", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call set GATT")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), GATTData::class.java)
            if (activity.checkBle()) {
                val uuids = activity.setUuids()
                // if there is uuid input, use the uuid input
                data.message?.let {
                    uuids["uuidServer"] = UUID.fromString(it)
                    Log.i("uuidServer", uuids["uuidServer"].toString())
                }
                val name = activity.getBleName()
                val mac = activity.getBleMac()
                val data2 = BleInfoData(name, mac, uuids) // 自己的蓝牙信息
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (data.flag) {
                        activity.startGATT(uuids, object : AdvertiseCallback() {
                            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                                function.onResult(
                                    activity.json(1, data2, "GATT succeed to start")
                                )
                                super.onStartSuccess(settingsInEffect)
                            }

                            override fun onStartFailure(errorCode: Int) {
                                function.onResult(
                                    activity.json(0, data2, "GATT fail to start, $errorCode")
                                )
                                super.onStartFailure(errorCode)
                            }
                        })
                    } else {
                        activity.stopGATT(object : AdvertiseCallback() {
                            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                                function.onResult(
                                    activity.json(1, data2, "GATT succeed to stop")
                                )
                                super.onStartSuccess(settingsInEffect)
                            }

                            override fun onStartFailure(errorCode: Int) {
                                function.onResult(
                                    activity.json(0, data2, "GATT fail to stop")
                                )
                                super.onStartFailure(errorCode)
                            }
                        })
                    }
                } else {
                    function.onResult(
                        activity.json(0, data2, "GATT broadcast fail to start, Android<5.1")
                    )
                }
            } else {
                function.onResult(
                    activity.json(0, null, "Bluetooth is not available, please turn on Bluetooth")
                )
            }
        })

        /* scan bluetooth devices
         *注意1：android 10之后无法再获取Bluetooth Mac地址
         *注意2：android 5.1之后支持蓝牙广播，将蓝牙作为服务器，可以发送UUID等信息，无需连接，仅限lowpower mode
         *注意3：范围：传统蓝牙模式 > lowpower mode
         *注意4：传统蓝牙模式扫描无法获取UUID，可以获取mac，lowpower全都可以获取
         *注意5：方案：针对android 5.1以下采用传统扫描方式，获取MAC地址
         */
        mWebView.registerHandler("scanBle", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call scanBle")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), ScanBleData::class.java)
            if (activity.checkBle()) {
                if (data.lowPower && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // 低功耗蓝牙的扫描回调
                    val scanLowCallback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult?) {
                            super.onScanResult(callbackType, result)
                            result?.let {
                                val device: BluetoothDevice = it.device
                                val serviceUuids = it.scanRecord?.serviceUuids
                                val returnData =
                                    activity.getBleDeviceData(device, it.rssi, serviceUuids)
                                returnData?.let { it2 ->
                                    Log.i("onScanResult", it2.toString())
                                    mWebView.callHandler(
                                        "OnBleScanResult",
                                        activity.json(
                                            1,
                                            it2,
                                            "Scan Bluetooth device: ${device.name}"
                                        )
                                    )
                                }
                            }
                        }

                        override fun onScanFailed(errorCode: Int) {
                            super.onScanFailed(errorCode)
                            val msg = "BleScan fail to scan errorCode: $errorCode"
                            Log.i(tag, msg)
                            Log.i(tag, errorCode.toString())
                            mWebView.callHandler("OnBleScanFailed", activity.json(0, errorCode))
                        }
                    }

                    val scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
                    Log.i(tag, "start low power Ble scan for ${data.time / 1000}s")
                    scanner.startScan(scanLowCallback)
                    // 已经开始扫描，低功耗蓝牙
                    function.onResult(activity.json(1, null, "Start scan Ble (Low Power)"))
                    // delay to stop
                    GlobalScope.launch {
                        delay(data.time)
                        scanner.stopScan(scanLowCallback)
                        Log.i(tag, "stop scan Ble")
                        mWebView.callHandler(
                            "OnBleFinishScan",
                            activity.json(1, null, "Finish Scan (Low Power Mode)")
                        )
                    }
                } else {
                    // 传统蓝牙扫描的回调
                    val scanCallback = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent) {
                            val action = intent.action
                            when {
                                action.equals(BluetoothDevice.ACTION_FOUND, ignoreCase = true) -> {
                                    // device found
                                    val device =
                                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)
                                    val returnData =
                                        device?.let { activity.getBleDeviceData(it, rssi.toInt()) }
                                    returnData?.let {
                                        Log.i("onScanResult", it.toString())
                                        mWebView.callHandler(
                                            "OnBleScanResult",
                                            activity.json(
                                                1,
                                                it,
                                                "Scan Bluetooth device: ${it.name}"
                                            )
                                        )
                                    }
                                }
                                action.equals(
                                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED, ignoreCase = true
                                ) -> {
                                    // discoveryFinished
                                    Log.i("BleScan", "Finish Discovery")
                                    mWebView.callHandler(
                                        "OnBleFinishScan",
                                        activity.json(1, null, "Finish Scan (Traditional)")
                                    )
                                }
                                action.equals(
                                    BluetoothAdapter.ACTION_DISCOVERY_STARTED, ignoreCase = true
                                ) -> {
                                    Log.i("BleScan", "Start Discovery")
                                    // 已经开始扫描，普通蓝牙
                                    function.onResult(
                                        activity.json(1, null, "Start scan Ble (Traditional)")
                                    )
                                }
                            }
                        }
                    }

                    val scanner = BluetoothAdapter.getDefaultAdapter()
                    val filter = IntentFilter()
                    filter.addAction(BluetoothDevice.ACTION_FOUND)
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    activity.registerReceiver(scanCallback, filter)
                    Log.i(tag, "start traditional Ble discovery for ${data.time / 1000}s")
                    scanner.startDiscovery()
                    // delay to stop
                    GlobalScope.launch {
                        delay(data.time)
                        Log.i(tag, "stop discovery Ble")
                        scanner.cancelDiscovery()
                        mWebView.callHandler(
                            "OnBleFinishScan",
                            activity.json(1, null, "Finish Scan (Traditional)")
                        )
                    }
                }
            } else {
                function.onResult(
                    activity.json(
                        0,
                        null,
                        "Bluetooth is not available, please check Bluetooth status"
                    )
                )
            }
        })

        // scan wifi
        mWebView.registerHandler("scanWifi", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call scanWifi")
            if (activity.checkWifi()) {
                val wifiManager =
                    activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val list = wifiManager.scanResults
                val data = ArrayList<WifiData>()
                for (item in list)
                    data.add(WifiData(item.BSSID, item.SSID, item.level, item.timestamp))
                function.onResult(
                    activity.json(1, data, "Wifi list")
                )
            } else {
                function.onResult(
                    activity.json(0, null, "Wifi is not available, please turn on Wifi Network")
                )
            }
        })

        // get location position, 统一只使用高德
        mWebView.registerHandler("location", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call get location")
            Log.i(tag, data.toString())
            val mLocationClient = AMapLocationClient(activity)
            val mLocationOption = AMapLocationClientOption()
            mLocationOption.locationMode =
                AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            mLocationOption.locationMode =
                AMapLocationClientOption.AMapLocationMode.Battery_Saving
            mLocationOption.isOnceLocation = true
            mLocationOption.isOnceLocationLatest = true
            mLocationClient.setLocationOption(mLocationOption)
            mLocationClient.stopLocation()
            mLocationClient.startLocation()
            mLocationClient.setLocationListener {
                Log.i(tag, it.toString())
                val data = LocData(
                    it.longitude,
                    it.latitude,
                    it.altitude,
                    it.provider,
                    it.speed,
                    it.time,
                    it.province,
                    null,
                    it.country,
                    it.adCode,
                    it.city,
                    it.district,
                    it.cityCode,
                    it.address,
                    it.street,
                    it.streetNum
                )
                function.onResult(activity.json(1, data, "GaoDe get location"))
            }
        })
        // get weather info
        mWebView.registerHandler(
            "weatherInfo",
            WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
                Log.i(tag, "js call get weather info")
                Log.i(tag, data.toString())
                val data = Gson().fromJson(data.toString(), WeatherReqData::class.java)
                val city = data.city
                val mQuery = if (data.type == 1) {
                    WeatherSearchQuery(city, WeatherSearchQuery.WEATHER_TYPE_LIVE)
                } else {
                    WeatherSearchQuery(city, WeatherSearchQuery.WEATHER_TYPE_FORECAST)
                }

                val mWeatherSearch = WeatherSearch(activity)
                mWeatherSearch.query = mQuery
                mWeatherSearch.searchWeatherAsyn()
                mWeatherSearch.setOnWeatherSearchListener(object :
                    WeatherSearch.OnWeatherSearchListener {
                    override fun onWeatherLiveSearched(p0: LocalWeatherLiveResult?, p1: Int) {
                        if (data.type == 1) {
                            Log.i(tag, "get weather live info")
                            val result = p0?.liveResult
                            result?.let {
                                val data = WeatherData(
                                    it.city,
                                    it.adCode,
                                    it.province,
                                    it.temperature,
                                    it.humidity,
                                    it.weather,
                                    it.windDirection,
                                    it.windPower,
                                    it.reportTime
                                )
                                function.onResult(activity.json(1, data, "realtime weather data"))
                            }
                        }
                    }

                    override fun onWeatherForecastSearched(
                        p0: LocalWeatherForecastResult?,
                        p1: Int
                    ) {
                        if (data.type == 2) {
                            Log.i(tag, "get weather forecast info")
                            function.onResult(
                                activity.json(
                                    1,
                                    p0?.forecastResult,
                                    "forecast weather data"
                                )
                            )
                        }
                    }
                })
            })

        // title, base64=true or false, if true, it will transform file to base64
        mWebView.registerHandler("cropper", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call get cropper img")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), CropperData::class.java)
            activity.imagePicker.setTitle(data.title)
            activity.imagePicker.setCropImage(true)
            activity.imagePicker.startChooser(activity, object : ImagePicker.Callback() {
                // 选择图片回调
                override fun onPickImage(imageUri: Uri) {
                    Log.i(tag, "origin img $imageUri")
                }

                // 裁剪图片回调
                override fun onCropImage(imageUri: Uri) {
                    Log.i(tag, "cropped img ${imageUri.path}")
                    if (data.base64) {
                        val baseImg = activity.base64Img(imageUri.path, data.quality)
                        function.onResult(
                            activity.json(
                                1,
                                "data:image/jpeg;base64,$baseImg",
                                "cropped image base64"
                            )
                        )
                    } else {
                        function.onResult(activity.json(1, "$imageUri", "cropped image path"))
                    }
                }

                // 自定义裁剪配置
                override fun cropConfig(builder: CropImage.ActivityBuilder) {
                    builder.setMultiTouchEnabled(false)
                        .setGuidelines(CropImageView.Guidelines.ON)
                        .setCropShape(CropImageView.CropShape.RECTANGLE)
                        .setRequestedSize(data.width, data.height)
                        .setAspectRatio(1, 1)
                }

                // 用户拒绝授权回调
                override fun onPermissionDenied(
                    requestCode: Int,
                    permissions: Array<String>,
                    grantResults: IntArray
                ) {
                }
            })
        })
        mWebView.registerHandler(
            "openMapActivity",
            WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
                Log.i(tag, "js call get cropper img")
                Log.i(tag, data.toString())
                val data = Gson().fromJson(data.toString(), OpenMapData::class.java)
                activity.goMap(data.path)
                function.onResult(activity.json(1, null, "map opened"))
            })
        mWebView.registerHandler(
            "getMac",
            WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
                Log.i(tag, "js call get mac address")
                val mac = Mac(activity.getBleMac(), activity.getWifiMac())
                function.onResult(activity.json(1, mac, "Mac Got"))
            })

        mWebView.registerHandler(
            "getVersion",
            WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
                Log.i(tag, "js call get version")
                val version =
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                var vnum = activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode.toLong()
                val data = VersionData(version, vnum)
                function.onResult(activity.json(1, data, "android version"))
            })
        mWebView.registerHandler(
            "updateApp",
            WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
                Log.i(tag, "js call update app")
                val data = Gson().fromJson(data.toString(), AppData::class.java)

                Log.i(tag, data.toString())
                val builder = AppUpdateManager.Builder(activity)
                builder.apkUrl(data.url)
                    .updateContent(data.description)
                    .confirmBgColor(Color.parseColor(data.confirmColor))
                    .cancelBgColor(Color.parseColor(data.cancelColor))
                    .build()

                function.onResult(activity.json(1, null, "update diaglog opened"))
            })
    }
}