package com.hicling.iictcling

import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.RelativeLayout
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.LocationSource
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.MyLocationStyle.LOCATION_TYPE_LOCATE
import com.google.gson.Gson
import wendu.webviewjavascriptbridge.WVJBWebView


class MapActivity : WebViewActivity(), LocationSource, AMapLocationListener {
    private var mMapView: MapView? = null
    private var aMap: AMap? = null
    private var myLocation: AMapLocation? = null

    //定位需要的数据
    private var mListener: LocationSource.OnLocationChangedListener? = null
    private var mLocationClient: AMapLocationClient? = null
    private val content = R.layout.activity_map
    override val tag = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(content)
        val bundle = this.intent.extras
        val path = bundle?.getString("path")
        // init web view
        findViewById<WVJBWebView>(R.id.webview)?.let {
            mWebView = it
            initWebView(it, false, url + path)
            initBridge(it)
        }
        // init map view
        findViewById<MapView>(R.id.map)?.let {
            it.onCreate(savedInstanceState)
            mMapView = it
        }
    }

    private fun initBridge(mWebView: WVJBWebView) {
        // zoom map camera size
        mWebView.registerHandler("zoomMap", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call zoom map")
            Log.i(tag, data.toString())
            val data = Gson().fromJson(data.toString(), MapZoomData::class.java)
            aMap?.moveCamera(CameraUpdateFactory.zoomTo(data.size))
            function.onResult(json(1, null, "success to zoom map to ${data.size}"))
        })
        // move the camera to the center of myself, blue point
        mWebView.registerHandler("moveCenter", WVJBWebView.WVJBHandler<Any?, Any?> { _, function ->
            Log.i(tag, "js call move center")
            myLocation?.let {
                aMap?.moveCamera(
                    CameraUpdateFactory.changeLatLng(LatLng(it.latitude, it.longitude))
                )
            }
            function.onResult(json(1, null, "success move to center of myself"))
        })
        mWebView.registerHandler("setMap", WVJBWebView.WVJBHandler<Any?, Any?> { data, function ->
            Log.i(tag, "js call set Map")
            val data = Gson().fromJson(data.toString(), MapData::class.java)
            val mViewMap = findViewById<RelativeLayout>(R.id.view_map)
            mViewMap?.let {
                // get display info
                val outMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(outMetrics)
                // 地图大小禁止大于屏幕大小
                if (data.width > outMetrics.widthPixels) data.width = outMetrics.widthPixels
                if (data.height > outMetrics.widthPixels) data.height = outMetrics.heightPixels

                val margin = MarginLayoutParams(it.layoutParams)
                margin.setMargins(data.left, data.top, data.right, data.bottom)

                val layoutParams = RelativeLayout.LayoutParams(margin)
                layoutParams.width = data.width
                layoutParams.height = data.height
                it.layoutParams = layoutParams

                Log.i(tag, data.toString())
                when (data.show) {
                    "visible" -> it.visibility = View.VISIBLE
                    "invisible" -> it.visibility = View.INVISIBLE
                    else -> it.visibility = View.GONE
                }
                setMyLocation(mMapView!!)
                function.onResult(json(1, null, "set map successfully"))
            }
        })
    }

    // init the map and location
    private fun setMyLocation(map: MapView) {
        Log.i(tag, "set my location")
        val myLocationStyle = MyLocationStyle()
        myLocationStyle.interval(5000)
        aMap = map.map
        aMap?.let {
            it.moveCamera(CameraUpdateFactory.zoomTo(12f))
            it.setLocationSource(this)
            it.isMyLocationEnabled = true
            it.setMyLocationType(LOCATION_TYPE_LOCATE)
            it.myLocationStyle = myLocationStyle
            it.uiSettings.isMyLocationButtonEnabled = true
            it.setOnMyLocationChangeListener {}
        }
        //myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_FOLLOW)
        myLocationStyle.showMyLocation(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        //在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        mMapView?.onDestroy()
        mLocationClient?.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mLocationClient?.startLocation()
        mMapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mLocationClient?.stopLocation()
        mMapView?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mMapView?.onSaveInstanceState(outState)
    }

    override fun activate(p0: LocationSource.OnLocationChangedListener?) {
        mListener = p0
        if (mLocationClient == null) {
            //初始化定位
            mLocationClient = AMapLocationClient(this)
            //初始化定位参数
            val mLocationOption = AMapLocationClientOption()
            mLocationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
            mLocationClient?.let {
                it.setLocationListener(this)
                it.setLocationOption(mLocationOption)
                it.startLocation() //启动定位
            }
        }
    }

    override fun deactivate() {
        mListener = null
        mLocationClient?.let {
            it.stopLocation()
            it.onDestroy()
        }
        mLocationClient = null
    }

    override fun onLocationChanged(aMapLocation: AMapLocation?) {
        if (mListener != null && aMapLocation != null) {
            if (aMapLocation.errorCode === 0) {
                myLocation = aMapLocation
                mListener?.onLocationChanged(aMapLocation) // 显示系统小蓝点
            } else {
                val errText =
                    "定位失败," + aMapLocation.errorCode.toString() + ": " + aMapLocation.errorInfo
                toast(R.string.location_error)
                Log.e("定位AmapErr", errText)
            }
        }
    }

}