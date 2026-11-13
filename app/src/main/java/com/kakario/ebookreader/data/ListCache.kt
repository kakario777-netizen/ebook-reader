package com.kakario.ebookreader.data

import android.content.Context
import com.kakario.ebookreader.model.FileEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 폴더 목록 기억해 두기 — 들어가면 저장된 목록을 바로 보여주고,
 * 그동안 서버에서 새로 받아 바뀐 게 있으면 고쳐 그린다.
 */
class ListCache private constructor(context: Context) {
    private val dir = File(context.filesDir, "listcache").apply { mkdirs() }

    private fun file(tag: String, path: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest("$tag|$path".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    fun read(tag: String, path: String): List<FileEntry>? = try {
        val f = file(tag, path)
        if (!f.exists()) null else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FileEntry(
                    name = o.getString("n"),
                    path = o.getString("p"),
                    isDirectory = o.optBoolean("d"),
                    size = o.optLong("s"),
                    modified = o.optLong("m"),
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    fun write(tag: String, path: String, list: List<FileEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(
                    JSONObject().put("n", e.name).put("p", e.path).put("d", e.isDirectory)
                        .put("s", e.size).put("m", e.modified)
                )
            }
            val f = file(tag, path)
            val tmp = File(f.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(f)
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile private var instance: ListCache? = null
        fun get(context: Context): ListCache =
            instance ?: synchronized(this) { instance ?: ListCache(context.applicationContext).also { instance = it } }
    }
}
