package com.kakario.ebookreader.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest

/**
 * 개인모드: 숨김으로 지정한 서버·폴더·책은 개인모드가 꺼져 있으면 어디에도 안 보인다.
 * 개인모드를 켜려면 4자리 숫자 코드. 켜진 동안만 숨김 지정/풀기를 할 수 있다.
 * 앱을 새로 켜면 항상 꺼진 상태(잠김)로 시작한다.
 *
 * 숨김 표시:
 *  - 서버 통째: "srv:<서버 id>"
 *  - 폴더·파일: "<위치 표시>|<경로>"  (위치 표시 = ftp<서버 id> 또는 local). 폴더면 그 안의 것도 전부 숨김.
 */
object Privacy {
    private var prefs: SharedPreferences? = null

    var unlocked by mutableStateOf(false)
        private set
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("privacy", Context.MODE_PRIVATE)
        prefs = p
        hidden = p.getStringSet("hidden", emptySet())!!.toSet()
    }

    val hasPin: Boolean get() = prefs?.getString("pin", null) != null

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("ebook-reader|$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        prefs?.edit()?.putString("pin", hash(pin))?.apply()
    }

    fun check(pin: String): Boolean = prefs?.getString("pin", null) == hash(pin)

    fun unlock() { unlocked = true }
    fun lock() { unlocked = false }

    private fun save(set: Set<String>) {
        hidden = set
        prefs?.edit()?.putStringSet("hidden", set)?.apply()
    }

    // ── 서버
    fun serverKey(id: Long) = "srv:$id"
    fun isServerHidden(id: Long) = serverKey(id) in hidden
    fun showServer(id: Long) = unlocked || !isServerHidden(id)

    // ── 폴더·파일 (tag = Origin.tag)
    fun pathKey(tag: String, path: String) = "$tag|${path.trimEnd('/')}"

    /** 이 항목이 직접 숨김으로 지정됐는지. */
    fun isMarked(tag: String, path: String) = pathKey(tag, path) in hidden

    /** 이 항목이나 그 위 폴더·서버가 숨김인지. */
    fun isHidden(tag: String, path: String): Boolean {
        if (tag.startsWith("ftp")) tag.removePrefix("ftp").toLongOrNull()?.let { if (isServerHidden(it)) return true }
        val p = path.trimEnd('/')
        val prefix = "$tag|"
        return hidden.any { k ->
            k.startsWith(prefix) && k.substring(prefix.length).let { h -> p == h || p.startsWith("$h/") }
        }
    }

    /** 지금 보여도 되는지. */
    fun show(tag: String, path: String) = unlocked || !isHidden(tag, path)

    fun toggle(key: String) {
        save(if (key in hidden) hidden - key else hidden + key)
    }
}
