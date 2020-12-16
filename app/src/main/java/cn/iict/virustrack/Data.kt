package cn.iict.virustrack

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/******
This file is for all data class use as models or we say old java beans.

Always, when it comes to json file type from script language, we need to use data class to convert them to objects!
 */

data class ToastData(val text: String?)
data class AlertData(val title: String?, val message: String?, val btnConfirm: String?)
data class LoadingData(val load: Boolean = false)
data class StatusBarData(val background: String?, val color: String?)
data class WebViewData(val url: String?, val loading: Boolean = false)
data class ResData(val status: Int?, val data: Any?, val msg: String?)
data class TimeData(val time: Long = 10000)
data class Mac(val bluetooth: String = "", val wifi: String = "")
data class ScanBleData(val time: Long = 10000, val lowPower: Boolean = false)
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

// 蓝牙基本信息，原生和前端交换
data class BleDeviceData(
    val mac: String?,
    val name: String?,
    val rssi: Int?,
    val type: Int?,
    val uuids: ArrayList<String>?,
    val bondState: Int?,
    val time: Long?

)

data class WifiData(
    val mac: String?,
    val name: String?,
    val rssi: Int?,
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
data class MarkData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val icon: String?,
    val title: String?,
    val desc: String?
)

// 设置蓝牙GATT服务时用的，自己的蓝牙信息，不完整
data class BleInfoData(
    val deviceName: String = "",
    val mac: String = "",
    val uuids: HashMap<String, UUID> = HashMap()
)

data class GATTData(
    val flag: Boolean = true,
    val message: String?
)

data class WinSizeData(
    val width: Int = 0,
    val height: Int = 0
)

data class NavBarData(
    val height: Int = 0,
    val show: Boolean = false,
    val indicator: Boolean = false
)

//体温
data class BandTemperatureData(
    val type: Int,
    val calendar: String,
    val startDate: String,
    val secondTime: Int,
    val bodyTemperature: Float,
    val bodySurfaceTemperature: Float,
    val ambientTemperature: Float
)

data class TemperatureInfoData(
    val calendar: String,
    val startDate: String,
    val secondTime: Int,
    val bodySurfaceTemperature: Float,
    val bodyTemperature: Float,
    val ambientTemperature: Float,
    val type: Int
)

// 计步
data class BandStepData(
    val step: Int,
    val distance: Float,
    val calories: Float,
    val runSteps: Int? = null,
    val runDistance: Float? = null,
    val runDurationTime: Int? = null,
    val walkSteps: Int? = null,
    val walkCalories: Float? = null,
    val walkDistance: Float? = null,
    val walkDurationTime: Int? = null
)

// 心率
data class BandRateData(
    val rate: Int?,
    val status: Int?
)

data class BandOneDayRateData(
    val current: Int,
    val lowest: Int,
    val average: Int,
    val highest: Int
)

data class BandSleepData(
    val total: Int,
    val deep: Int,
    val light: Int,
    val awakeTime: Int,
    val awakeCount: Int,
    val begin: Int,
    val end: Int,
    val sleepStatus: IntArray,
    val durationTime: IntArray,
    val timePoint: IntArray

) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BandSleepData

        if (!sleepStatus.contentEquals(other.sleepStatus)) return false
        if (!durationTime.contentEquals(other.durationTime)) return false
        if (!timePoint.contentEquals(other.timePoint)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sleepStatus.contentHashCode()
        result = 31 * result + durationTime.contentHashCode()
        result = 31 * result + timePoint.contentHashCode()
        return result
    }
}

// 血压
data class BloodPressureData(
    val p0: Int?,
    val p1: Int?,
    val p2: Int?
)

data class BandDateData(
    val date: String
)

// 开关类，只有一个参数，只有true或者false
data class SwitchData(
    val flag: Boolean = true,
    val delay: Long = 1000
)

data class AppData(
    val version: String = "",
    val vnum: Int = 0,
    val createtime: Int = 0,
    val description: Array<String>,
    val url: String = "",
    val type: Int = 1,
    val confirmColor: String? = "#2196f3",
    val cancelColor: String? = "##b1b1b1"
)

data class VersionData(
    val version: String,
    val vnum: Long
)
