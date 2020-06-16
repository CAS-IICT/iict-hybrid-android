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
data class LocType(val type: Int = 1) //1 native, 2 高德
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
    val cityCode: String? = null,
    val address: String? = null,
    val street: String? = null,
    val streetNum: String? = null
)
