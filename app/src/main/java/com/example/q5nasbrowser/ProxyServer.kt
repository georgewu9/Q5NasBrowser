package com.example.q5nasbrowser

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import jcifs.CIFSContext
import java.io.InputStream

/**
 * 本地微型 HTTP 代理：把远端 SMB 文件流式提供给播放器。
 * 播放器只需支持 http 视频即可，无需懂 SMB 协议。
 * 支持 Range 请求（拖动进度/秒开）。
 */
class ProxyServer(
    port: Int,
    private val ctx: CIFSContext,
    private val host: String,
    private val path: String
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val rangeHeader = session.headers["range"]
        val total = SmbBrowser.length(ctx, host, path)
        // 音视频共用的 MIME 表，保证播放器能正确识别 flac/wav/mkv 等格式
        val mime = MediaType.mimeOf(path)

        var start = 0L
        var end = if (total > 0) total - 1 else 0L
        if (rangeHeader != null) {
            val m = Regex("bytes=(\\d+)-(\\d*)").find(rangeHeader)
            if (m != null) {
                start = m.groupValues[1].toLong()
                if (m.groupValues[2].isNotBlank()) end = m.groupValues[2].toLong()
            }
        }

        val stream: InputStream = SmbBrowser.openStream(ctx, host, path)
        skipFully(stream, start)

        val status = if (rangeHeader != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val len = if (total > 0) (end - start + 1) else -1L

        val res: Response = if (len >= 0) {
            newFixedLengthResponse(status, mime, stream, len)
        } else {
            newChunkedResponse(status, mime, stream)
        }

        if (total > 0) {
            res.addHeader("Content-Range", "bytes $start-$end/$total")
            res.addHeader("Accept-Ranges", "bytes")
        }
        return res
    }

    private fun skipFully(stream: InputStream, n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) break
            remaining -= skipped
        }
    }
}
