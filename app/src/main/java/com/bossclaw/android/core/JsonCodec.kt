package com.bossclaw.android.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonCodec {
    val gson: Gson = Gson()

    fun encode(value: Any?): String = gson.toJson(value)

    inline fun <reified T> decode(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { gson.fromJson<T>(raw, object : TypeToken<T>() {}.type) }.getOrDefault(fallback)
    }
}
