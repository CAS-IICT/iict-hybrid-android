package com.hicling.iictcling


/******
This file is for all data class use as models or we say old java beans.

Always, when it comes to json file type from script language, we need to use data class to convert them to objects!
 */

data class ToastData(val text: String)
data class AlertData(val title: String, val message: String, val btnConfirm: String)
data class LoadingData(val load: Boolean)
data class StatusBarData(val color: String)
data class WebViewData(val url: String, val loading: Boolean)
data class SignInData(val username: String, val password: String)
data class SignUpData(val username: String, val password: String, val repassword: String)
data class ResData(val status: Int, val data: String?, val msg: String)
data class TimeData(val time: Long = 10000)
