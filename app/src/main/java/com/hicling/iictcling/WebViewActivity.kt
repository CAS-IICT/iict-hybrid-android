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
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.Toast
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.google.gson.Gson
import wendu.webviewjavascriptbridge.WVJBWebView


@SuppressLint("Registered")
open class WebViewActivity : FragmentActivity() {

    open var url: String = ""
    open val tag: String = this.javaClass.simpleName
    private var loading: Boolean = false
    private val content: Int = R.layout.activity_webview // overridable
    open var mWebView: WVJBWebView? = null
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getPermission()
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
    open fun initWebView(mWebView: WVJBWebView, loading: Boolean = false) {
        Log.i(tag, "init url $url")
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
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.i(tag, "onReceivedError")
                view?.loadUrl("file:///android_asset/error.html")
                onLoadError()
                super.onReceivedError(view, request, error)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                Log.i(tag, "onReceivedHttpError")
                view?.loadUrl("file:///android_asset/error.html")
                onLoadError()
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }
        // deal with error page for android < 6.0 mash
        mWebView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    if (title.contains("404")
                        || title.contains("500")
                        || title.contains("Error")
                        || title.contains("找不到网页")
                        || title.contains("网页无法打开")
                        || title.contains("not available")
                    ) {
                        Log.i(tag, "title find error, android<6.0")
                        view.loadUrl("file:///android_asset/error.html")
                        onLoadError()
                        super.onReceivedTitle(view, title)
                    }
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

    // give standard response as JSON to frontend
    open fun json(status: Int, data: Any? = null, msg: String? = ""): String {
        return Gson().toJson(ResData(status, data, msg))
    }

    // use to init all the bridge functions to handle js call, only for the activities which extends jsActivity
    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initBridge(mWebView: WVJBWebView) {
        Log.i(tag, "initBridgeFunc")
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
            val toast = Toast.makeText(this, data.text, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.BOTTOM, 0, 100)
            toast.show()
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
                Log.i(tag, "js call set navbar")
                Log.i(tag, data.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Log.i(tag, "call set navbar success")
                    val data = Gson().fromJson(data.toString(), StatusBarData::class.java)
                    val color = Color.parseColor(data.color)
                    Log.i(tag, color.toString())
                    this.window.statusBarColor = color
                    function.onResult(json(1))
                } else {
                    Log.i(tag, "call set navbar fail")
                    function.onResult(json(0, null, "Low Version, need more than Android5.0"))
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
        mWebView.registerHandler("scanBle", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call scanBle")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), TimeData::class.java)
            if (checkBle()) {
                val scanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        super.onScanResult(callbackType, result)
                        val msg = "BleScan results"
                        Log.i(tag, msg)
                        Log.i(tag, result.toString())
                        function.onResult(json(1, result, msg))
                    }

                    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                        super.onBatchScanResults(results)
                        val msg = "Batch Scan Results"
                        Log.i(tag, msg)
                        Log.i(tag, results.toString())
                        function.onResult(json(1, results, msg))
                    }

                    override fun onScanFailed(errorCode: Int) {
                        super.onScanFailed(errorCode)
                        val msg = "BleScan fail to scan errorCode: $errorCode"
                        Log.i(tag, msg)
                        Log.i(tag, errorCode.toString())
                        function.onResult(json(0, null, msg))
                    }
                }
                val scanner = BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner
                Log.i(tag, "start scan for ${data.time / 1000}s")
                scanner.startScan(scanCallback)
                handler.postDelayed({
                    scanner.stopScan(scanCallback)
                    Log.i(tag, "stop scan ble")
                }, data.time)
            }
        })
        // get location position, 1表示安卓原生定位，2表示高德定位
        mWebView.registerHandler("location", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call get location")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), LocType::class.java)
            if (data.type == 2) { //高德
                val mLocationClient = AMapLocationClient(applicationContext)
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
            } else { //原生定位
                val locationManager =
                    this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val providers: List<String> = locationManager.getProviders(true)
                var locationProvider: String? = null
                // through GPS
                val msg: String
                when {
                    providers.contains(LocationManager.GPS_PROVIDER) -> {
                        msg = "GPS get location"
                        Log.i(tag, msg)
                        locationProvider = LocationManager.GPS_PROVIDER
                    }
                    // through network
                    providers.contains(LocationManager.NETWORK_PROVIDER) -> {
                        msg = "network get location"
                        Log.i(tag, msg)
                        //如果是Network
                        locationProvider = LocationManager.NETWORK_PROVIDER
                    }
                    else -> {
                        msg = "location is not available"
                        val i = Intent()
                        i.action = Settings.ACTION_LOCATION_SOURCE_SETTINGS
                        this.startActivity(i)
                    }
                }
                if (locationProvider != null) {
                    val location = locationManager.getLastKnownLocation(locationProvider)
                    val addList =
                        Geocoder(this).getFromLocation(location.latitude, location.longitude, 1)
                    val data = LocData(
                        location.longitude,
                        location.latitude,
                        location.altitude,
                        location.provider,
                        location.speed,
                        location.time,
                        addList[0].adminArea,
                        addList[0].countryCode,
                        addList[0].countryName,
                        addList[0].postalCode,
                        addList[0].locality,
                        addList[0].subLocality
                    )
                    function.onResult(json(1, data, msg))
                } else {
                    function.onResult(json(0, null, msg))
                }
            }
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
                Log.i(tag, "Activity cant go back")
                isExit++
                exit()
                false
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun exit() {
        if (isExit < 2) {
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
        Manifest.permission.CHANGE_NETWORK_STATE
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
}

