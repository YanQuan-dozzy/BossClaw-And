package com.bossclaw.android.domain

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * 本地简历文档解析：简历原文不出机器，解析全程在本机完成。
 * - PDF：PdfBox-Android（Apache 2.0，GitHub: TomRoush/PdfBox-Android）
 * - DOCX：解压 word/document.xml，用系统 XmlPullParser 抽取正文（零额外依赖）
 * - TXT / MD：UTF-8 → GB18030 → windows-1252 逐级回落解码
 * 旧版 .doc（OLE2/RTF）暂不支持，提示用户另存为 DOCX/PDF。
 */
object DocumentParser {

    fun extract(context: Context, uri: Uri, fileName: String, mimeType: String?): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val isPdf = extension == "pdf" || mimeType == "application/pdf"
        val isDocx = extension == "docx" ||
            mimeType?.contains("wordprocessingml") == true
        val isLegacyDoc = extension == "doc" || mimeType == "application/msword"

        val raw = when {
            isPdf -> readPdf(context, uri)
            isDocx -> readDocx(context, uri)
            isLegacyDoc -> throw IllegalStateException("暂不支持旧版 .doc，请在 Word 中另存为 DOCX 或 PDF 后导入")
            else -> readPlainText(context, uri)
        }
        val text = normalize(raw)
        if (text.isBlank()) {
            throw IllegalStateException("文档正文为空，可能是扫描件 / 图片版，请改用文本可复制的 PDF 或直接粘贴正文")
        }
        if ((isPdf || isDocx) && text.replace(Regex("""\s+"""), "").length < 20) {
            throw IllegalStateException("提取到的文本过少，可能是扫描件或加密文档，请改用可复制文本的 PDF/DOCX")
        }
        return text
    }

    private fun readPdf(context: Context, uri: Uri): String {
        val stream = open(context, uri)
        return stream.use { input ->
            val document = PDDocument.load(input)
            document.use {
                PDFTextStripper().apply { sortByPosition = true }.getText(it)
            }
        }
    }

    private fun readDocx(context: Context, uri: Uri): String {
        open(context, uri).use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") return parseWordXml(zip)
                    entry = zip.nextEntry
                }
            }
        }
        throw IllegalStateException("DOCX 中未找到正文（word/document.xml），文件可能已损坏或加密")
    }

    private fun parseWordXml(input: InputStream): String {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val builder = StringBuilder()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':').orEmpty()
            when (event) {
                XmlPullParser.START_TAG -> when (tag) {
                    "t" -> inText = true
                    "tab" -> builder.append('\t')
                    "br", "cr" -> builder.append('\n')
                }
                XmlPullParser.TEXT -> if (inText) builder.append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> when (tag) {
                    "t" -> inText = false
                    "p", "tr" -> builder.append('\n')
                    "tc" -> builder.append('\t')
                }
            }
            event = parser.next()
        }
        return builder.toString()
    }

    private fun readPlainText(context: Context, uri: Uri): String {
        val bytes = open(context, uri).use { it.readBytes() }
        return decodeTextBytes(bytes)
    }

    /** 先严格 UTF-8，失败按中文文档代码页 GB18030，最后 windows-1252 兜底。 */
    private fun decodeTextBytes(bytes: ByteArray): String {
        runCatching {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        }
        runCatching {
            val gb = Charset.forName("GB18030")
            val text = gb.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            val bad = text.count { it == '\uFFFD' }
            if (bad <= maxOf(1, text.length / 10)) return text
        }
        return runCatching { String(bytes, Charset.forName("windows-1252")) }.getOrDefault(String(bytes))
    }

    private fun open(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("无法读取文件，请重新选择")

    private fun normalize(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace("\u00a0", " ")
        .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"""), "")
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n[ \t]+"""), "\n")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}
