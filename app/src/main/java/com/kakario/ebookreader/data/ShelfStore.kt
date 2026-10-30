package com.kakario.ebookreader.data

import android.content.Context
import com.kakario.ebookreader.model.FileEntry
import org.json.JSONArray
import org.json.JSONObject

/** 책장에 꽂은 책 한 권. serverId가 null이면 내 폰에 있는 책. */
data class ShelfBook(
    val id: Long,
    val serverId: Long?,
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
) {
    fun toEntry(): FileEntry = FileEntry(name, path, isDirectory = false, size = size, modified = modified)

    fun matches(serverId: Long?, path: String): Boolean = this.serverId == serverId && this.path == path
}

/** 책장 목록을 폰 안에 저장/불러오기. */
class ShelfStore(context: Context) {
    private val prefs = context.getSharedPreferences("shelf", Context.MODE_PRIVATE)

    fun load(): List<ShelfBook> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ShelfBook(
                    id = o.getLong("id"),
                    serverId = if (o.isNull("serverId")) null else o.getLong("serverId"),
                    path = o.getString("path"),
                    name = o.getString("name"),
                    size = o.optLong("size"),
                    modified = o.optLong("modified"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(books: List<ShelfBook>) {
        val arr = JSONArray()
        books.forEach { b ->
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("serverId", b.serverId ?: JSONObject.NULL)
                    .put("path", b.path)
                    .put("name", b.name)
                    .put("size", b.size)
                    .put("modified", b.modified)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "shelf_list"
    }
}
