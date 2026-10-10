package com.example.telepromptertg

import android.content.Context

/** Wrapper around SharedPreferences for Teleprompter settings and text. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("teleprompter", Context.MODE_PRIVATE)

    var text: String
        get() = sp.getString("text", "") ?: ""
        set(v) {
            sp.edit().putString("text", v).apply()
        }

    var wpm: Int
        get() = sp.getInt("wpm", 130)
        set(v) {
            sp.edit().putInt("wpm", v).apply()
        }

    var size: Int
        get() = sp.getInt("size", 22)
        set(v) {
            sp.edit().putInt("size", v).apply()
        }
}
