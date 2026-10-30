package com.kakario.ebookreader.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import com.kakario.ebookreader.book.Origin
import com.kakario.ebookreader.data.ShelfBook
import com.kakario.ebookreader.data.ShelfStore
import com.kakario.ebookreader.model.FileEntry

/** 첫 화면 책장 — 어디서든 '책장에 꽂기/빼기'를 할 수 있게 앱 전체에서 하나. */
class Shelf(private val store: ShelfStore) {
    val books = mutableStateListOf<ShelfBook>().apply { addAll(store.load()) }

    fun contains(origin: Origin, entry: FileEntry): Boolean =
        books.any { it.matches(origin.server?.id, entry.path) }

    /** 꽂혀 있으면 빼고, 없으면 맨 앞에 꽂는다. 결과: 지금 책장에 있으면 true. */
    fun toggle(origin: Origin, entry: FileEntry): Boolean {
        val existing = books.firstOrNull { it.matches(origin.server?.id, entry.path) }
        val nowOn = if (existing != null) {
            books.remove(existing)
            false
        } else {
            books.add(
                0,
                ShelfBook(
                    id = System.currentTimeMillis(),
                    serverId = origin.server?.id,
                    path = entry.path,
                    name = entry.name,
                    size = entry.size,
                    modified = entry.modified,
                ),
            )
            true
        }
        store.save(books)
        return nowOn
    }

    fun remove(book: ShelfBook) {
        books.removeAll { it.id == book.id }
        store.save(books)
    }

    /** 서버를 지우면 그 서버의 책도 책장에서 뺀다. */
    fun removeServer(serverId: Long) {
        if (books.removeAll { it.serverId == serverId }) store.save(books)
    }
}

val LocalShelf = compositionLocalOf<Shelf?> { null }
