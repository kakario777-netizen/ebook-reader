package com.kakario.ebookreader.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.kakario.ebookreader.model.FileEntry

/**
 * 책 하나의 읽은 상태.
 *  - 만화·PDF: position = 페이지(0부터), total = 전체 페이지 수
 *  - TXT·EPUB: position = 지금 페이지가 시작하는 글자 위치, total = 전체 글자 수
 * total이 0이면 아직 안 열어본 책.
 */
data class ReadProgress(val position: Int, val total: Int, val done: Boolean) {
    val started: Boolean get() = total > 0
    val finished: Boolean get() = done
    val fraction: Float
        get() = when {
            done -> 1f
            total > 0 -> (position.toFloat() / total).coerceIn(0f, 1f)
            else -> 0f
        }
    val percent: Int get() = (fraction * 100).toInt().coerceIn(0, 100)
}

/**
 * 책마다 마지막으로 본 위치를 기억한다 (다시 열면 이어보기 + 목록에 진행률 표시).
 * 앱 전체에서 하나만 쓴다.
 */
class ProgressStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    /** 뷰어를 닫을 때마다 올라가는 번호 — 목록이 진행률을 다시 그리게 하는 신호. */
    var version by mutableIntStateOf(0)
        private set

    /** origin: "local" 또는 "ftp<서버id>" */
    fun keyFor(originTag: String, entry: FileEntry): String =
        "$originTag|${entry.path}|${entry.size}"

    fun get(key: String): ReadProgress =
        ReadProgress(
            prefs.getInt(key, 0),
            prefs.getInt("$key#total", 0),
            prefs.getBoolean("$key#done", false),
        )

    /** 마지막으로 읽은 시각 (밀리초, 0 = 모름). 서버 기록과 비교해 더 최근 쪽을 따른다. */
    fun time(key: String): Long = prefs.getLong("$key#time", 0L)

    /** 읽는 중에 위치 저장 → 서버에도 몇 초 뒤 올라간다 (FTP 책만). */
    fun set(key: String, position: Int, total: Int, done: Boolean) {
        val now = System.currentTimeMillis()
        write(key, position, total, done, now)
        onLocalChange?.invoke(key, position, total, done, now)
    }

    /** 서버에서 받은 기록으로 바꾸기 (다시 서버로 올리지는 않음). 더 최근일 때만. */
    fun mergeFromServer(key: String, position: Int, total: Int, done: Boolean, time: Long): Boolean {
        if (time <= time(key)) return false
        write(key, position, total, done, time)
        return true
    }

    private fun write(key: String, position: Int, total: Int, done: Boolean, time: Long) {
        prefs.edit()
            .putInt(key, position)
            .putInt("$key#total", total)
            .putBoolean("$key#done", done)
            .putLong("$key#time", time)
            .apply()
    }

    /** 모든 기록 (처음 한 번 서버에 올릴 때) */
    fun allKeys(): List<String> = prefs.all.keys.filter { '#' !in it }

    /** 위치가 바뀌면 불린다 (ServerSync가 등록). */
    var onLocalChange: ((key: String, position: Int, total: Int, done: Boolean, time: Long) -> Unit)? = null

    fun clear(key: String) {
        prefs.edit().remove(key).remove("$key#total").remove("$key#done").remove("$key#time").apply()
        version++
    }

    fun bump() {
        version++
    }

    companion object {
        @Volatile private var instance: ProgressStore? = null

        fun get(context: Context): ProgressStore =
            instance ?: synchronized(this) {
                instance ?: ProgressStore(context.applicationContext).also { instance = it }
            }
    }
}
