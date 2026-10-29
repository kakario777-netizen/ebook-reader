package com.kakario.ebookreader.book

import com.kakario.ebookreader.model.RangeSource
import java.net.URLDecoder

/**
 * EPUB 읽기 (EPUB은 ZIP 안에 XHTML 파일들이 들어 있는 형식).
 *  1) META-INF/container.xml 에서 목차 파일(OPF) 위치를 찾고
 *  2) OPF에서 읽는 순서(spine)와 표지 그림을 알아낸 뒤
 *  3) 각 XHTML을 글자 문단·그림으로 바꾼다.
 * 꾸밈(CSS)은 쓰지 않는다 — 흰 바탕에 글자만.
 */
object Epub {

    class Book(
        val title: String?,
        /** 읽는 순서대로 XHTML 파일의 ZIP 안 경로 */
        val spine: List<String>,
        /** 표지 그림의 ZIP 안 경로 (모르면 null) */
        val coverPath: String?,
        val entries: Map<String, ZipReader.Entry>,
    )

    fun open(src: RangeSource): Book? {
        val list = ZipReader.listEntries(src) ?: return null
        val entries = LinkedHashMap<String, ZipReader.Entry>()
        list.forEach { entries[it.name] = it }

        val container = text(src, entries, "META-INF/container.xml") ?: return null
        val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(container)
            ?.groupValues?.get(1) ?: return null
        val opf = text(src, entries, opfPath) ?: return null
        val opfDir = opfPath.substringBeforeLast('/', "")

        // manifest: id → (경로, 종류, 속성)
        class Item(val path: String, val type: String, val props: String)
        val items = HashMap<String, Item>()
        val spineIds = ArrayList<String>()
        var coverId: String? = null
        TAG.findAll(opf).forEach { m ->
            val tag = m.groupValues[1].lowercase()
            val attrs = attrs(m.groupValues[2])
            when (tag) {
                "item" -> {
                    val id = attrs["id"] ?: return@forEach
                    val href = attrs["href"] ?: return@forEach
                    items[id] = Item(resolve(opfDir, href), attrs["media-type"].orEmpty(), attrs["properties"].orEmpty())
                }
                "itemref" -> {
                    if (attrs["linear"] != "no") attrs["idref"]?.let { spineIds += it }
                }
                "meta" -> {
                    if (attrs["name"] == "cover") coverId = attrs["content"]
                }
            }
        }
        val spine = spineIds.mapNotNull { items[it]?.path }.filter { find(entries, it) != null }

        val cover = items.values.firstOrNull { "cover-image" in it.props.split(' ') }?.path
            ?: coverId?.let { items[it] }?.takeIf { it.type.startsWith("image") }?.path
            ?: items.values.firstOrNull {
                it.type.startsWith("image") && it.path.substringAfterLast('/').contains("cover", ignoreCase = true)
            }?.path

        val title = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(opf)
            ?.groupValues?.get(1)?.let { Html.decodeEntities(it).trim() }?.ifBlank { null }

        return Book(title, spine, cover, entries)
    }

    /** 목록에 쓸 표지 그림 바이트. OPF가 알려준 표지 → 첫 페이지의 첫 그림 순서로 찾는다. */
    fun coverBytes(src: RangeSource): ByteArray? {
        val book = open(src) ?: return null
        book.coverPath?.let { p -> find(book.entries, p)?.let { e -> ZipReader.extract(src, e)?.let { return it } } }
        for (page in book.spine.take(2)) {
            val html = text(src, book.entries, page) ?: continue
            val img = Html.parse(html, page.substringBeforeLast('/', "")).firstOrNull { it is Html.Block.Image }
                as? Html.Block.Image ?: continue
            find(book.entries, img.path)?.let { e -> ZipReader.extract(src, e)?.let { return it } }
        }
        return null
    }

    fun find(entries: Map<String, ZipReader.Entry>, path: String): ZipReader.Entry? =
        entries[path] ?: entries.values.firstOrNull { it.name.equals(path, ignoreCase = true) }

    fun bytes(src: RangeSource, entries: Map<String, ZipReader.Entry>, path: String): ByteArray? =
        find(entries, path)?.let { ZipReader.extract(src, it) }

    fun text(src: RangeSource, entries: Map<String, ZipReader.Entry>, path: String): String? =
        bytes(src, entries, path)?.let { TextDecode.decodeXml(it) }

    /** OPF·XHTML 안의 상대 경로를 ZIP 안 경로로 바꾼다 ("../Images/a.jpg" 등). */
    fun resolve(baseDir: String, href: String): String {
        val clean = href.substringBefore('#').let {
            try {
                URLDecoder.decode(it.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                it
            }
        }
        if (clean.startsWith("/")) return clean.trimStart('/')
        val parts = ArrayList<String>()
        if (baseDir.isNotEmpty()) parts += baseDir.split('/')
        for (p in clean.split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts += p
            }
        }
        return parts.joinToString("/")
    }

    private val TAG = Regex("<(?:[\\w-]+:)?(item|itemref|meta)\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([\\w:.-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")

    fun attrs(s: String): Map<String, String> {
        val map = HashMap<String, String>()
        ATTR.findAll(s).forEach { m ->
            val name = m.groupValues[1].lowercase().substringAfter(':')
            val value = m.groups[3]?.value ?: m.groups[4]?.value ?: ""
            map[name] = Html.decodeEntities(value)
        }
        return map
    }
}
