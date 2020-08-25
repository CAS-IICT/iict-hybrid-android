package com.hicling.iictcling

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery
import com.google.gson.Gson
import com.linchaolong.android.imagepicker.ImagePicker
import com.linchaolong.android.imagepicker.cropper.CropImage
import com.linchaolong.android.imagepicker.cropper.CropImageView
import wendu.webviewjavascriptbridge.WVJBWebView

class Init {
    open val tag: String = this.javaClass.simpleName
    private val handler = Handler()

    open fun initBridge(mWebView: WVJBWebView, activity: WebViewActivity) {
        Log.i(tag, "WebViewActivity: initBridge")
        // back
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
                            null,
                            "set status bar successfully, color:${data.color}, bg:${data.background}"
                        )
                    )
                } else {
                    Log.i(tag, "call set status bar fail")
                    function.onResult(
                        activity.json(
                            0,
                            null,
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
                blueAdapter.enable()
                function.onResult(activity.json(1, null, "Ble is enabled"))
            } else {
                function.onResult(activity.json(0, null, "Fail to turn on, no ble"))
            }
        })

        mWebView.registerHandler("setGATT", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call set GATT")
            if (activity.checkBle()) {
                val uuids = activity.setUuids()
                Log.i("UUIDS", uuids.toString())
                activity.initGATT(uuids)
                function.onResult(
                    activity.json(
                        1,
                        Gson().toJson(uuids),
                        "GATT Ble broadcast server starts"
                    )
                )
            } else {
                function.onResult(
                    activity.json(
                        0,
                        null,
                        "Bluetooth is not available, please turn on Bluetooth"
                    )
                )
            }
        })

        // scan bluetooth devices
        mWebView.registerHandler("scanBle", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call scanBle")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), ScanBleData::class.java)
            if (activity.checkBle()) {
                // 整理并向前端发送扫描到的设备
                fun bleCallFrontEnd(
                    device: BluetoothDevice,
                    rssi: Int,
                    serviceUuids: List<ParcelUuid>? = null
                ) {
                    // 过滤无名设备
                    if (device.name == null) return
                    val uuids = device.uuids
                    val list = ArrayList<String>()
                    // 经典蓝牙uuid，一般获取不到
                    uuids?.let {
                        for (uuid in it) {
                            list.add(uuid.toString())
                        }
                    }
                    // low power uuid，可以获得serviceuuid
                    serviceUuids?.let {
                        for (uuid in it) {
                            list.add(uuid.toString())
                        }
                    }
                    val data = BleDeviceData(
                        device.address,
                        device.name,
                        rssi,
                        device.type,
                        list,
                        device.bondState,
                        System.currentTimeMillis() / 1000
                        //it.timestampNanos
                    )
                    activity.bleDeviceHash[device.address] = device
                    mWebView.callHandler("BleOnScanResult", activity.json(1, data))
                }

                // 传统蓝牙扫描的回调
                val scanCallback: BroadcastReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent) {
                        val action = intent.action
                        when {
                            action.equals(BluetoothDevice.ACTION_FOUND, ignoreCase = true) -> {
                                // device found
                                val device =
                                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0)
                                device.fetchUuidsWithSdp()
                                val uuid =
                                    intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                                if (uuid == null) Log.i("null", "null uuid")
                                bleCallFrontEnd(device, rssi.toInt())
                            }
                            action.equals(
                                BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                                ignoreCase = true
                            ) -> {
                                // discoveryFinished
                                Log.i("BleScan", "Finish Discovery")
                            }
                            action.equals(
                                BluetoothAdapter.ACTION_DISCOVERY_STARTED, ignoreCase = true
                            ) -> {
                                Log.i("BleScan", "Start Discovery")
                            }
                        }
                    }
                }

                if (data.lowPower && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // 低功耗蓝牙的扫描回调
                    val scanLowCallback = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, result: ScanResult?) {
                            super.onScanResult(callbackType, result)
                            Log.i("onScanResult", result.toString())
                            result?.let {
                                val device: BluetoothDevice = it.device
                                val serviceUuids = it.scanRecord?.serviceUuids
                                bleCallFrontEnd(device, it.rssi, serviceUuids)
                            }
                        }

                        override fun onScanFailed(errorCode: Int) {
                            super.onScanFailed(errorCode)
                            val msg = "BleScan fail to scan errorCode: $errorCode"
                            Log.i(tag, msg)
                            Log.i(tag, errorCode.toString())
                            mWebView.callHandler("BleOnScanFailed", activity.json(0, errorCode))
                        }
                    }
                    // 已经开始扫描，低功耗蓝牙
                    function.onResult(activity.json(1, null, " start scan Ble in low power"))
                    val scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
                    Log.i(tag, "start low power Ble scan for ${data.time / 1000}s")
                    scanner.startScan(scanLowCallback)
                    // 定时关闭蓝牙扫描
                    handler.postDelayed({
                        scanner.stopScan(scanLowCallback)
                        Log.i(tag, "stop scan Ble")
                    }, data.time)
                } else {
                    // 已经开始扫描，普通蓝牙
                    function.onResult(activity.json(1, null, " start scan Ble in all"))
                    val scanner = BluetoothAdapter.getDefaultAdapter()
                    Log.i(tag, "start discovery, all Ble devices")
                    val filter = IntentFilter()
                    filter.addAction(BluetoothDevice.ACTION_FOUND)
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    activity.registerReceiver(scanCallback, filter)
                    scanner.startDiscovery()
                }
            } else {
                function.onResult(
                    activity.json(
                        0,
                        null,
                        "Bluetooth is not available, please turn on Bluetooth"
                    )
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
    }
}