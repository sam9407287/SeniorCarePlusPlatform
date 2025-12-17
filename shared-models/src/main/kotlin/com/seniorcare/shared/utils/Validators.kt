package com.seniorcare.shared.utils

/**
 * 数据验证工具类
 */
object Validators {
    
    /**
     * 验证心率范围
     */
    fun isValidHeartRate(heartRate: Int?): Boolean {
        return heartRate == null || (heartRate in 30..220)
    }
    
    /**
     * 验证血氧范围
     */
    fun isValidSpO2(spO2: Int?): Boolean {
        return spO2 == null || (spO2 in 70..100)
    }
    
    /**
     * 验证血压范围
     */
    fun isValidBloodPressure(systolic: Int?, diastolic: Int?): Boolean {
        val systolicValid = systolic == null || (systolic in 50..250)
        val diastolicValid = diastolic == null || (diastolic in 30..150)
        return systolicValid && diastolicValid
    }
    
    /**
     * 验证体温范围
     */
    fun isValidTemperature(temp: Double?): Boolean {
        return temp == null || (temp in 30.0..45.0)
    }
    
    /**
     * 验证湿度范围
     */
    fun isValidHumidity(humidity: Double?): Boolean {
        return humidity == null || (humidity in 0.0..100.0)
    }
    
    /**
     * 验证 MAC 地址格式
     */
    fun isValidMacAddress(mac: String): Boolean {
        if (mac.isBlank()) return false
        // 简单验证：至少6个字符
        return mac.length >= 6
    }
}

