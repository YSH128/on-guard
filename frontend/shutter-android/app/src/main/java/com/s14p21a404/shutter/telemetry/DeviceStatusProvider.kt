package com.s14p21a404.shutter.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import kotlin.math.abs

object DeviceStatusProvider {

    // 기기 배터리 온도를 섭씨(°C) 단위로 반환합니다.
    fun getDeviceTemperature(context: Context): Float {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
        
        val temperatureInt = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        return temperatureInt / 10.0f
    }

    // App PSS Memory (int, KB)
    // 현재 앱이 할당받아 사용 중인 물리적 메모리
    fun getAppPssMemory(): Int {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss // 기본 단위가 KB이며 int형을 반환합니다.
    }

    // Thermal Throttling Status (int)
    // 하드웨어 쓰로틀링 상태 (Android 10 이상 지원)
    fun getThermalThrottlingStatus(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val osStatus = powerManager.currentThermalStatus

            return when (osStatus) {
                PowerManager.THERMAL_STATUS_NONE -> 0       // 제한 없음
                PowerManager.THERMAL_STATUS_LIGHT -> 1      // 가벼운 쓰로틀링 (UX 영향 적음)
                PowerManager.THERMAL_STATUS_MODERATE -> 2   // 중간 쓰로틀링 (UX 영향 발생 가능)
                PowerManager.THERMAL_STATUS_SEVERE -> 3     // 심각한 쓰로틀링 (성능 저하 뚜렷함)
                PowerManager.THERMAL_STATUS_CRITICAL -> 4   // 임계 단계 (기기 보호를 위해 앱 강제 종료 가능)
                PowerManager.THERMAL_STATUS_EMERGENCY -> 5  // 긴급 단계 (핵심 기능만 작동)
                PowerManager.THERMAL_STATUS_SHUTDOWN -> 6   // 종료 직전 (곧 전원 꺼짐)
                else -> 0
            }
        }
        return 0 // API 29 미만 기기에서는 기본값(정상) 반환
    }

    // Thread CPU Time (long, ms)
    // 주의: 반드시 AI 모델 추론이 실행되는 코루틴/스레드 내부에서 호출해야 해당 스레드의 연산 시간을 알 수 있습니다.
    fun getThreadCpuTimeMs(): Long {
        val cpuTimeNanos = Debug.threadCpuTimeNanos()
        return if (cpuTimeNanos > 0) cpuTimeNanos / 1_000_000L else 0L // 나노초(ns) -> 밀리초(ms) 변환
    }

    // Battery Current Now (float, mA)
    // 추론 중 전력 피크 (방전 전류량)
    fun getBatteryCurrentNow(context: Context): Float {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        // API는 마이크로암페어(μA)를 반환합니다. 제조사마다 방전 시 음수(-)를 반환하기도 하므로 절대값을 취합니다.
        val currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return abs(currentMicroAmps) / 1000f // μA -> mA 변환
    }

    // Device Model Name (String)
    // 기기 제조사와 모델 번호를 결합하여 반환합니다. (예: "Samsung SM-S921N")
    fun getDeviceModelName(): String {
        val manufacturer = Build.MANUFACTURER // 예: "samsung", "Google"
        val model = Build.MODEL               // 예: "SM-S921N", "Pixel 8"

        // 모델명에 제조사 이름이 이미 포함되어 있는 경우 중복 방지
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            val capitalizedManufacturer = manufacturer.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            }
            "$capitalizedManufacturer $model"
        }
    }
}