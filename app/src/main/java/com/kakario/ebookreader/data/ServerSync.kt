package com.kakario.ebookreader.data

import android.content.Context
import android.os.Build
import com.kakario.ebookreader.ftp.FtpBrowser
import com.kakario.ebookreader.model.FileEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * FTP 서버 동기화 — 코믹뷰어와 같은 규칙으로 어느 기기에서 봐도 같은 기록·표지를 쓴다.
 *
 *  위치: 책이 있는 폴더 안 숨김 폴더 .cvsync/
 *   - 표지:      .cvsync/<책파일이름>.<파일크기>.jpg   (긴 변 360px JPEG)
 *   - 읽기 기록: .cvsync/progress.json
 *       {"version":1,"books":{"<책파일이름>":{"size":..,"pos":..,"total":..,"done":..,"unit":"page|char","time":..,"device":".."}}}
 *  규칙:
 *   - 읽는 중 위치가 바뀌면 3초 뒤(마지막 것만) 서버에 올린다. 올릴 때 서버 파일을 먼저 받아
 *     책마다 time이 더 최근인 쪽을 남긴다 (다른 기기 기록을 지우지 않게).
 *   - 책을 열 때·폴더를 열 때 서버 기록을 받아, 더 최근이면 폰 안 기록을 그걸로 바꾼다.
 *   - 연결이 안 되면 폰 안 기록으로 동작하고, 다음에 맞춘다.
 *  내 폰(로컬) 파일은 대상이 아니다.
 */
class ServerSync private constructor(context: Context) {
    private val progress = ProgressStore.get(context)
    private val prefs = context.getSharedPreferences("server_sync", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val device = (Build.MANUFACTURER + " " + Build.MODEL).trim()

    /** 서버 id → 서버 정보 (앱이 서버 목록을 바꿀 때마다 알려준다) */
    private val servers = ConcurrentHashMap<Long, FtpServer>()
    /** 서버마다 동기화 전용 연결 (목록 보기·책 받기와 서로 막지 않게) */
    private val conns = ConcurrentHashMap<Long, FtpBrowser>()
    /** 폴더마다 한 번에 하나씩 쓰기 */
    private val folderLocks = ConcurrentHashMap<String, Mutex>()
    /** 올릴 기록: "서버id|폴더" → (책이름 → 기록) */
    private val pending = ConcurrentHashMap<String, MutableMap<String, JSONObject>>()
    private val timers = ConcurrentHashMap<String, Job>()

    /** 화면 위에 띄울 동기화 문구 (null = 안 띄움) */
    val status = MutableStateFlow<String?>(null)

    init {
        progress.onLocalChange = { key, pos, total, done, time -> onLocal(key, pos, total, done, time) }
    }

    fun setServers(list: List<FtpServer>) {
        servers.clear()
        list.forEach { servers[it.id] = it }
    }

    private fun conn(server: FtpServer): FtpBrowser = conns.getOrPut(server.id) { FtpBrowser(server) }

    private fun folderOf(path: String): String = path.substringBeforeLast('/', "").ifEmpty { "/" }
    private fun nameOf(path: String): String = path.substringAfterLast('/')
    private fun syncDir(folder: String): String = if (folder.endsWith("/")) "$folder.cvsync" else "$folder/.cvsync"

    private fun unitOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "txt", "epub" -> "char"
            else -> "page"
        }

    /** 키 "ftp<서버id>|경로|크기" → (서버, 경로, 크기) */
    private fun parseKey(key: String): Triple<FtpServer, String, Long>? {
        val parts = key.split('|')
        if (parts.size < 3 || !parts[0].startsWith("ftp")) return null
        val server = servers[parts[0].removePrefix("ftp").toLongOrNull() ?: return null] ?: return null
        val size = parts.last().toLongOrNull() ?: return null
        val path = parts.subList(1, parts.size - 1).joinToString("|")
        return Triple(server, path, size)
    }

    // ───────────────────── 올리기

    private fun onLocal(key: String, pos: Int, total: Int, done: Boolean, time: Long) {
        val (server, path, size) = parseKey(key) ?: return
        val name = nameOf(path)
        val rec = JSONObject()
            .put("size", size).put("pos", pos).put("total", total).put("done", done)
            .put("unit", unitOf(name)).put("time", time).put("device", device)
        val fk = "${server.id}|${folderOf(path)}"
        pending.getOrPut(fk) { ConcurrentHashMap() }[name] = rec
        // 연달아 넘기면 마지막 것만 3초 뒤에
        timers[fk]?.cancel()
        timers[fk] = scope.launch {
            delay(3_000)
            flushFolder(fk)
        }
    }

    /** 책을 닫을 때 등: 기다리지 말고 바로 올리기 */
    fun flushAll() {
        pending.keys.toList().forEach { fk ->
            timers[fk]?.cancel()
            scope.launch { flushFolder(fk) }
        }
    }

    private suspend fun flushFolder(fk: String) {
        val recs = pending.remove(fk) ?: return
        if (recs.isEmpty()) return
        val id = fk.substringBefore('|').toLongOrNull() ?: return
        val folder = fk.substringAfter('|')
        val server = servers[id] ?: return
        val ok = mergeAndWrite(server, folder, recs)
        if (!ok) {
            // 실패하면 다시 담아두고 다음 기회에
            val back = pending.getOrPut(fk) { ConcurrentHashMap() }
            recs.forEach { (n, r) -> if (newer(r, back[n])) back[n] = r }
        }
    }

    private fun newer(a: JSONObject?, b: JSONObject?): Boolean =
        a != null && (b == null || a.optLong("time") > b.optLong("time"))

    /** 서버 기록을 받아 책마다 더 최근 것을 남기고 다시 올린다. */
    private suspend fun mergeAndWrite(server: FtpServer, folder: String, recs: Map<String, JSONObject>): Boolean {
        val lock = folderLocks.getOrPut("${server.id}|$folder") { Mutex() }
        return lock.withLock {
            runCatching {
                val path = syncDir(folder) + "/progress.json"
                val ftp = conn(server)
                val remote = ftp.readSmall(path)?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
                    ?: JSONObject().put("version", 1)
                val books = remote.optJSONObject("books") ?: JSONObject().also { remote.put("books", it) }
                var changed = false
                recs.forEach { (name, r) ->
                    if (newer(r, books.optJSONObject(name))) {
                        books.put(name, r)
                        changed = true
                    }
                }
                if (changed) ftp.writeSmall(path, remote.toString().toByteArray(Charsets.UTF_8)) else true
            }.getOrDefault(false)
        }
    }

    // ───────────────────── 받기

    /** 폴더의 서버 기록을 받아 폰 안 기록에 합친다. 바뀐 게 있으면 true. */
    suspend fun pullFolder(server: FtpServer, folder: String, entries: List<FileEntry>? = null): Boolean =
        withContext(Dispatchers.IO) {
            val bytes = runCatching { conn(server).readSmall(syncDir(folder) + "/progress.json") }.getOrNull()
                ?: return@withContext false
            val books = runCatching { JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("books") }.getOrNull()
                ?: return@withContext false
            var changed = false
            books.keys().forEach { name ->
                val r = books.optJSONObject(name) ?: return@forEach
                val size = r.optLong("size")
                // 같은 이름·같은 크기의 책에만 적용
                val path = (if (folder.endsWith("/")) folder else "$folder/") + name
                if (entries != null && entries.none { it.path == path && it.size == size }) return@forEach
                val key = "ftp${server.id}|$path|$size"
                if (progress.mergeFromServer(key, r.optInt("pos"), r.optInt("total"), r.optBoolean("done"), r.optLong("time"))) {
                    changed = true
                }
            }
            if (changed) withContext(Dispatchers.Main) { progress.bump() }
            changed
        }

    /** 책을 열기 직전: 서버 기록 확인 (최대 4초만 기다림) */
    suspend fun pullBeforeOpen(server: FtpServer, entry: FileEntry) {
        withTimeoutOrNull(4_000) { pullFolder(server, folderOf(entry.path)) }
    }

    /** 폴더 목록을 열었을 때: 뒤에서 조용히 받아 진행률 표시를 맞춘다 */
    fun pullFolderAsync(server: FtpServer, folder: String, entries: List<FileEntry>) {
        if (entries.none { it.isBook }) return
        scope.launch { pullFolder(server, folder, entries) }
    }

    // ───────────────────── 표지

    fun thumbPath(entry: FileEntry): String = syncDir(folderOf(entry.path)) + "/" + nameOf(entry.path) + "." + entry.size + ".jpg"

    /** 서버에 올라와 있는 표지 (없으면 null) */
    suspend fun readThumb(server: FtpServer, entry: FileEntry): ByteArray? =
        runCatching { conn(server).readSmall(thumbPath(entry)) }.getOrNull()?.takeIf { it.size > 100 }

    /** 이 기기에서 만든 표지를 서버에도 올려 다른 기기가 쓰게 한다 */
    fun uploadThumb(server: FtpServer, entry: FileEntry, jpeg: ByteArray) {
        scope.launch { runCatching { conn(server).writeSmall(thumbPath(entry), jpeg) } }
    }

    // ───────────────────── 처음 한 번: 폰에 있던 읽기 기록 모두 올리기 (꺼도 이어서)

    fun startInitialUpload() {
        if (prefs.getBoolean("initial_done", false)) return
        scope.launch {
            delay(1_500)
            val keys = progress.allKeys().filter { it.startsWith("ftp") && parseKey(it) != null }
            val groups = keys.groupBy { k -> parseKey(k)!!.let { "${it.first.id}|${folderOf(it.second)}" } }
            val doneSet = prefs.getStringSet("initial_folders", emptySet())!!.toMutableSet()
            val todo = groups.filterKeys { it !in doneSet }
            if (todo.isEmpty()) {
                prefs.edit().putBoolean("initial_done", true).apply()
                return@launch
            }
            var count = doneSet.size
            val total = groups.size
            var failed = false
            for ((fk, ks) in todo) {
                status.value = "읽기 정보 서버 동기화 중 ($count/$total)"
                val id = fk.substringBefore('|').toLong()
                val server = servers[id] ?: continue
                val recs = ks.associate { k ->
                    val (_, path, size) = parseKey(k)!!
                    val p = progress.get(k)
                    nameOf(path) to JSONObject()
                        .put("size", size).put("pos", p.position).put("total", p.total).put("done", p.done)
                        .put("unit", unitOf(nameOf(path)))
                        .put("time", progress.time(k).takeIf { it > 0 } ?: 1L) // 시각 모르는 옛 기록은 가장 오래된 것으로
                        .put("device", device)
                }
                if (mergeAndWrite(server, fk.substringAfter('|'), recs)) {
                    doneSet += fk
                    prefs.edit().putStringSet("initial_folders", HashSet(doneSet)).apply()
                    count++
                } else {
                    failed = true
                }
            }
            if (!failed) prefs.edit().putBoolean("initial_done", true).apply()
            status.value = null
        }
    }

    companion object {
        @Volatile private var instance: ServerSync? = null
        fun get(context: Context): ServerSync =
            instance ?: synchronized(this) { instance ?: ServerSync(context.applicationContext).also { instance = it } }
    }
}
