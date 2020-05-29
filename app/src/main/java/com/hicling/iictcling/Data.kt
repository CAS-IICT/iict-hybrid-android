package com.hicling.iictcling


/******
This file is for all data class use as models or we say old java beans.

Always, when it comes to json file type from script language, we need to use data class to convert them to objects!
 */

data class ToastData(val text: String)
data class AlertData(val title: String, val message: String, val btnConfirm: String)
data class LoadingData(val load: Boolean)
data class StatusBarData(val color: String)
