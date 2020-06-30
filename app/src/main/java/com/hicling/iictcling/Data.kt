package com.hicling.iictcling

import android.location.Address


/******
This file is for all data class use as models or we say old java beans.

Always, when it comes to json file type from script language, we need to use data class to convert them to objects!
 */

data class ToastData(val text: String?)
data class AlertData(val title: String?, val message: String?, val btnConfirm: String?)
data class LoadingData(val load: Boolean = false)
data class StatusBarData(val color: String?)
data class WebViewData(val url: String?, val loading: Boolean = false)
data class SignInData(val username: String?, val password: String?)
data class SignUpData(val username: String?, val password: String?, val rePassword: String?)
data class ResData(val status: Int?, val data: Any?, val msg: String?)
data class TimeData(val time: Long = 10000)
data class CropperData(
    val title: String = "",
    val base64: Boolean = true,
    val quality: Int = 100,
    val width: Int = 500,
    val height: Int = 500
)

data class LocData(
    val longitude: Double?,
    val latitude: Double?,
    val altitude: Double?,
    val provider: String?,
    val speed: Float?,
    val time: Long?,
    val province: String?,
    val countryCode: String?,
    val country: String?,
    val zipCode: String?,
    val city: String?,
    val area: String?,
    val cityPhoneNum: String? = null,
    val address: String? = null,
    val street: String? = null,
    val streetNum: String? = null
)

data class WeatherReqData(
    val city: String?,
    val type: Int = 0 //1实时天气，2预测天气
)

data class WeatherData(
    val city: String?,
    val cityCode: String?,
    val province: String?,
    val temp: String?,
    val humidity: String?,
    val weather: String?,
    val windDirection: String?,
    val windPower: String?,
    val time: String?
)

data class BleDeviceData(
    val mac: String?,
    val name: String?,
    val rssi: Int?,
    val type: Int?,
    val bondState: Int?,
    val time: Long?

)

data class MapData(
    var show: String = "gone",
    var height: Int = 0,
    var width: Int = 0,
    var top: Int = 0,
    var left: Int = 0,
    var right: Int = 0,
    var bottom: Int = 0
)

data class OpenMapData(val path: String = "")
data class MapZoomData(val size: Float = 12f)
