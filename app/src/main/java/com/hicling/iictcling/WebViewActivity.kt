/*
*
* devilyouwei
* devilyouwei@gmail.com
* 2020-5-20
* IICT copyright
* 中国科学院计算所苏州
* MIT License
* All activities with webview component need to extend this class
*/

package com.hicling.iictcling

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Toast
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
import net.vidageek.mirror.dsl.Mirror
import wendu.webviewjavascriptbridge.WVJBWebView
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.NetworkInterface
import java.util.*
import kotlin.collections.ArrayList


@Suppress("DEPRECATION")
@SuppressLint("Registered")
open class WebViewActivity : Activity() {

    open var url = "http://192.168.1.79:8080"

    //open var url = "http://192.168.1.103:8080" //前端
    open val tag: String = this.javaClass.simpleName
    private var loading: Boolean = false
    private val content: Int = R.layout.activity_webview // overridable
    open var mWebView: WVJBWebView? = null
    private val handler = Handler()
    private val imagePicker: ImagePicker = ImagePicker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getPermission()
        getWifiMac()
        getBleMac()
        getBleUUID()
        val bundle = this.intent.extras
        bundle?.getBoolean("loading")?.let { loading = it }
        // 当有url传入的时候在本类中直接调用initWebView，该类一般不被继承了
        bundle?.getString("url")?.let {
            setContentView(content)
            val mWebView: WVJBWebView = findViewById(R.id.webview)
            this.mWebView = mWebView
            url = it
            initWebView(mWebView, loading)
        }
    }

    // initial function, make all the config for jsbridge
    @SuppressLint("SetJavaScriptEnabled")
    open fun initWebView(
        mWebView: WVJBWebView,
        loading: Boolean = false,
        otherUrl: String? = null
    ) {
        if (otherUrl != null) url = otherUrl
        Log.i(tag, "initWebView: $url")
        showLoading(loading)
        val webSettings: WebSettings = mWebView.settings

        webSettings.setSupportZoom(false)
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.defaultTextEncodingName = "utf-8"
        webSettings.loadsImagesAutomatically = true
        //多窗口
        webSettings.supportMultipleWindows()
        //允许访问文件
        webSettings.allowFileAccess = true
        //开启javascript
        webSettings.javaScriptEnabled = true
        //支持通过JS打开新窗口
        //webSettings.javaScriptCanOpenWindowsAutomatically = true
        //关闭webview中缓存
        webSettings.setAppCacheEnabled(true)
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings.domStorageEnabled = true //DOM Storage

        mWebView.overScrollMode = WVJBWebView.OVER_SCROLL_NEVER // 取消WebView中滚动或拖动到顶部、底部时的阴影
        mWebView.scrollBarStyle = WVJBWebView.SCROLLBARS_INSIDE_OVERLAY // 取消滚动条白边效果
        //获取触摸焦点
        mWebView.requestFocusFromTouch()
        // >= 19(SDK4.4)启动硬件加速，否则启动软件加速
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webSettings.loadsImagesAutomatically = true //支持自动加载图片
        } else {
            mWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webSettings.loadsImagesAutomatically = false
        }

        //deal with the error network
        mWebView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                onLoadFinish()
                super.onPageFinished(view, url)
                Log.i(tag, "Webview finish loading: $url")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                Log.i(tag, "onReceivedHttpError")
                //view?.loadUrl("file:///android_asset/error.html")
                onLoadError()
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }

        // deal with error page for android < 6.0 mash
        mWebView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                Log.i(tag, "$title")
                if (title.contains("404")
                    || title.contains("找不到")
                    || title.contains("Error")
                    || title.contains("500")
                    || title.contains("无法打开")
                    || title.contains("not available")
                    || title.contains("not found")
                ) {
                    Log.i(tag, "title find error, android<6.0")
                    view.loadUrl("file:///android_asset/error.html")
                    onLoadError()
                    super.onReceivedTitle(view, title)
                }
            }

        }
        mWebView.loadUrl(url)
        initBridge(mWebView)
    }

    open fun onLoadFinish() {
        showLoading(false)
    }

    open fun onLoadError() {
        Log.i(tag, "register refresh error page: $url")
        mWebView?.registerHandler(
            "refreshErrorPage",
            WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
                function.onResult(json(1, url, "refresh the error page"))
            })
        showLoading(false)
    }

    // 获取mac地址
    open fun getWifiMac(): String {
        try {
            Log.i(
                "ble",
                Settings.Secure.getString(applicationContext.contentResolver, "bluetooth_address")
            )
            val all = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in all) {
                if (!nif.name.equals("wlan0", ignoreCase = true)) continue

                val macBytes = nif.hardwareAddress ?: return ""

                val res1 = StringBuilder()
                for (b in macBytes) {
                    //res1.append(Integer.toHexString(b & 0xFF) + ":");
                    res1.append(String.format("%02X:", b))
                }

                if (res1.isNotEmpty()) {
                    res1.deleteCharAt(res1.length - 1)
                }
                Log.i("wlan0", res1.toString())
                return res1.toString()
            }
        } catch (e: Exception) {
            Log.i("error", e.message)
        }

        return "02:00:00:00:00:00"
    }

    open fun getBleMac(): String {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val bluetoothManagerService = Mirror().on(bluetoothAdapter).get().field("mService")
            if (bluetoothManagerService == null) {
                Log.w(tag, "no bluetoothManagerService")
                return "02:00:00:00:00:00"
            }
            val address =
                Mirror().on(bluetoothManagerService).invoke().method("getAddress").withoutArgs()
            return if (address != null && address is String) {
                Log.i("BleMac", address.toString())
                address.toString()
            } else "02:00:00:00:00:00"
        } catch (e: Exception) {
            Log.i("BleMacError", e.message)
            return "02:00:00:00:00:00"
        }
    }

    open fun getBleUUID(): ArrayList<String> {
        val list = ArrayList<String>()
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val uuids =
                Mirror().on(bluetoothAdapter).invoke().method("getUuids").withoutArgs() as Array<*>
            for (uuid in uuids) {
                list.add(uuid.toString())
            }
        } catch (e: Exception) {
            Log.e("uuid_error", e.message)
        }
        return list
    }

    // give standard response as JSON to frontend
    open fun json(status: Int, data: Any? = null, msg: String? = ""): String {
        return Gson().toJson(ResData(status, data, msg))
    }

    // use to init all the bridge functions to handle js call, only for the activities which extends jsActivity
    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    open fun initBridge(mWebView: WVJBWebView) {
        Log.i(tag, "WebViewActivity: initBridge")
        // back
        mWebView.registerHandler("back", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call back")
            if (mWebView.canGoBack()) {
                mWebView.goBack()
                function.onResult(json(1))
            } else {
                function.onResult(json(1))
                this.finish()
            }
        })
        // toast
        mWebView.registerHandler("toast", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call toast")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), ToastData::class.java)
            toast(data.text)
            function.onResult(json(1))
        })
        // alert
        mWebView.registerHandler("alert", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call alert")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), AlertData::class.java)
            AlertDialog.Builder(this)
                .setMessage(data.message)
                .setTitle(data.title)
                .setPositiveButton(data.btnConfirm) { _, _ ->
                    function.onResult(json(1))
                }.create().show()
        })
        // loading
        mWebView.registerHandler("loading", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call loading")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), LoadingData::class.java)
            showLoading(data.load)
            function.onResult(json(1))
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
                        this.window.statusBarColor = color
                    }
                    data.color?.let {
                        val decor: View = this.window.decorView
                        if (data.color == "dark") {
                            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        } else {
                            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        }
                    }
                    function.onResult(
                        json(
                            1,
                            null,
                            "set status bar successfully, color:${data.color}, bg:${data.background}"
                        )
                    )
                } else {
                    Log.i(tag, "call set status bar fail")
                    function.onResult(json(0, null, "Low Version, need more than Android 5.0"))
                }
            })
        // start a new webview activity and go to the specified url
        mWebView.registerHandler("go", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call go other webview, new activity")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), WebViewData::class.java)
            goWebView(data.url, data.loading)
            function.onResult(json(1, null, data.url))
        })
        // check bluetooth device status
        mWebView.registerHandler("checkBle", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call checkBle")
            if (checkBle()) {
                val msg = "Bluetooth is enabled"
                Log.i(tag, msg)
                function.onResult(json(1, null, msg))
            } else {
                val msg = "Bluetooth is unavailable"
                Log.i(tag, msg)
                function.onResult(json(0, null, msg))
            }
        })
        // turn on bluetooth device
        mWebView.registerHandler("turnOnBle", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call turn on Ble")
            val blueAdapter = BluetoothAdapter.getDefaultAdapter()
            if (blueAdapter != null) {
                blueAdapter.enable()
                function.onResult(json(1, null, "Ble is enabled"))
            } else {
                function.onResult(json(0, null, "Fail to turn on, no ble"))
            }
        })
        // scan bluetooth devices
        mWebView.registerHandler("scanBle", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call scanBle")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), ScanBleData::class.java)
            if (checkBle()) {
                // 整理并向前端发送扫描到的设备
                fun bleCallFrontEnd(device: BluetoothDevice, rssi: Int) {
                    // 过滤无名设备
                    if (device.name == null) return
                    val uuids = device.uuids
                    val list = ArrayList<String>()
                    uuids?.let {
                        for (uuid in uuids) {
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
                    mWebView.callHandler("BleOnScanResult", json(1, data))
                }
                // 低功耗蓝牙的扫描回调
                val scanLowCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        super.onScanResult(callbackType, result)
                        Log.i("onScanResult", result.toString())
                        result?.let {
                            val device = it.device
                            bleCallFrontEnd(device, it.rssi)
                        }
                    }

                    /*
                    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                        super.onBatchScanResults(results)
                        Log.i("onBatchScanResults", results.toString())
                        mWebView.callHandler("BleOnBatchScanResult", json(1, results))
                    }
                    */

                    override fun onScanFailed(errorCode: Int) {
                        super.onScanFailed(errorCode)
                        val msg = "BleScan fail to scan errorCode: $errorCode"
                        Log.i(tag, msg)
                        Log.i(tag, errorCode.toString())
                        mWebView.callHandler("BleOnScanFailed", json(0, errorCode))
                    }
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
                                if(uuid==null) Log.i("null","null uuid")
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

                if (data.lowPower) {
                    // 已经开始扫描，低功耗蓝牙
                    function.onResult(json(1, null, " start scan Ble in low power"))
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
                    function.onResult(json(1, null, " start scan Ble in all"))
                    val scanner = BluetoothAdapter.getDefaultAdapter()
                    Log.i(tag, "start discovery, all Ble devices")
                    val filter = IntentFilter()
                    filter.addAction(BluetoothDevice.ACTION_FOUND)
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    this.registerReceiver(scanCallback, filter)
                    scanner.startDiscovery()
                }
            } else {
                function.onResult(
                    json(
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
            val mLocationClient = AMapLocationClient(this)
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
                function.onResult(json(1, data, "GaoDe get location"))
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

                val mWeatherSearch = WeatherSearch(this)
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
                                function.onResult(json(1, data, "realtime weather data"))
                            }
                        }
                    }

                    override fun onWeatherForecastSearched(
                        p0: LocalWeatherForecastResult?,
                        p1: Int
                    ) {
                        if (data.type == 2) {
                            Log.i(tag, "get weather forecast info")
                            function.onResult(json(1, p0?.forecastResult, "forecast weather data"))
                        }
                    }
                })
            })

        // title, base64=true or false, if true, it will transform file to base64
        mWebView.registerHandler("cropper", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call get cropper img")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), CropperData::class.java)
            imagePicker.setTitle(data.title)
            imagePicker.setCropImage(true)
            imagePicker.startChooser(this, object : ImagePicker.Callback() {
                // 选择图片回调
                override fun onPickImage(imageUri: Uri) {
                    Log.i(tag, "origin img $imageUri")
                }

                // 裁剪图片回调
                override fun onCropImage(imageUri: Uri) {
                    Log.i(tag, "cropped img ${imageUri.path}")
                    if (data.base64) {
                        val baseImg = base64Img(imageUri.path, data.quality)
                        function.onResult(
                            json(1, "data:image/jpeg;base64,$baseImg", "cropped image base64")
                        )
                    } else {
                        function.onResult(json(1, "$imageUri", "cropped image path"))
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
                goMap(data.path)
                function.onResult(json(1, null, "map opened"))
            })
    }

    private fun checkBle(): Boolean {
        val blueAdapter = BluetoothAdapter.getDefaultAdapter()
        return blueAdapter != null && blueAdapter.isEnabled

    }

    // go to other activity
    private fun goWebView(url: String?, loading: Boolean = false) {
        val intent = Intent(this, WebViewActivity::class.java)
        val bundle = Bundle()
        bundle.putString("url", url)
        bundle.putBoolean("loading", loading)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun goMap(path: String = "") {
        val intent = Intent(this, MapActivity::class.java)
        val bundle = Bundle()
        bundle.putString("path", path)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun showLoading(load: Boolean) {
        val modal: LinearLayout = findViewById(R.id.load)
        if (load) modal.visibility = View.VISIBLE
        else modal.visibility = View.INVISIBLE
    }

    private var isExit = 0
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.i(tag, "on key down")
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return if (mWebView!!.canGoBack()) {
                Log.i(tag, "webview go back")
                mWebView?.goBack()
                true
            } else {
                Log.i(tag, "Activity go back")
                isExit++
                exit()
                false
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun exit() {
        if (isExit < 2 && tag == "MainActivity") {
            Toast.makeText(applicationContext, R.string.exit, Toast.LENGTH_SHORT).show()
            handler.postDelayed({ isExit-- }, 2000L)
        } else {
            this.finish()
        }
    }

    /**
     * 解决：android>6，定位等权限，蓝牙问题
     */
    private val accessCode = 102
    private val permissions: Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.INTERNET,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CHANGE_NETWORK_STATE,
        Manifest.permission.CAMERA
    )
    private var countRequest = 0

    // get bluetooth permission
    private fun getPermission() {
        countRequest++
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            var permissionCheck = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionCheck += checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, accessCode)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // check permission of images picker
        imagePicker.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
        when (requestCode) {
            accessCode -> if (checkPermission(grantResults)) {
                Log.i(tag, "onRequestPermissionsResult: 用户允许权限 accessCode:$accessCode")
            } else {
                Log.i(tag, "onRequestPermissionsResult: 拒绝搜索设备权限 accessCode:$accessCode")
                if (countRequest > 2) {
                    // ask User to grant permission manually
                    AlertDialog.Builder(this)
                        .setMessage(R.string.open_permission_req)
                        .setTitle(R.string.request)
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            goIntentSetting()
                        }.create().show()
                } else getPermission()
            }
        }
    }

    private fun checkPermission(grantResults: IntArray): Boolean {
        for (grantResult in grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                return false
            }
        }
        return true
    }

    // open app settings let user grant the permission
    private fun goIntentSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", this.packageName, null)
        intent.data = uri
        try {
            this.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    open fun toast(text: String?) {
        text?.let {
            val toast = Toast.makeText(this, it, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.BOTTOM, 0, 100)
            toast.show()
        }
    }

    open fun toast(text: Int?) {
        text?.let {
            val toast = Toast.makeText(this, it, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.BOTTOM, 0, 100)
            toast.show()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        imagePicker.onActivityResult(this, requestCode, resultCode, data)
    }

    // file to base64
    open fun base64Img(path: String?, quality: Int = 100): String? {
        if (path == null || TextUtils.isEmpty(path)) {
            throw Error("error: path is empty")
        }
        return try {
            val bitmap = BitmapFactory.decodeFile(path)
            val os = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, quality, os)
            val bytes = os.toByteArray()
            Log.i(tag, "path:$path, quality:$quality")
            String(Base64.encode(bytes, Base64.DEFAULT))
        } catch (e: IOException) {
            Log.e(tag, e.message)
            null
        }
    }

    // base64 to bitmap
    open fun base64ToBitmap(base64Data: String?): Bitmap? {
        val bytes =
            Base64.decode(base64Data, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onDestroy() {
        super.onDestroy()
        mWebView?.destroy()
    }
}

