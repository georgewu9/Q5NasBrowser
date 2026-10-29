package com.example.q5nasbrowser

import jcifs.CIFSContext
import jcifs.smb.SmbFile
import java.io.InputStream

/**
 * 基于 jcifs-ng 的 SMB 浏览：列出共享、列出目录内容、打开文件流、取文件大小。
 * 支持 SMB1 ~ SMB3，因此能访问 Buffalo（可能 SMB1）与 WD My Cloud Home（SMB2/3）。
 */
object SmbBrowser {

    /** 列出某主机的共享目录。 */
    fun listShares(ctx: CIFSContext, host: String): List<FileItem> {
        val root = SmbFile("smb://$host/", ctx)
        return root.listFiles()
            .filter { it.isDirectory }
            .map {
                val shareName = it.name.trimEnd('/')
                FileItem(name = shareName, isDirectory = true, path = shareName, host = host)
            }
    }

    /** 列出某共享/目录下的内容，目录在前、文件在后，按名称排序。 */
    fun listFiles(ctx: CIFSContext, host: String, path: String): List<FileItem> {
        val url = "smb://$host/$path"
        val dir = SmbFile(if (url.endsWith("/")) url else "$url/", ctx)
        return dir.listFiles().map { sf ->
            val isDir = sf.isDirectory
            val childPath = if (path.endsWith("/")) path + sf.name else "$path/${sf.name}"
            FileItem(
                name = sf.name.trimEnd('/'),
                isDirectory = isDir,
                path = childPath.trimEnd('/'),
                host = host
            )
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun openStream(ctx: CIFSContext, host: String, path: String): InputStream {
        return SmbFile("smb://$host/$path", ctx).inputStream
    }

    fun length(ctx: CIFSContext, host: String, path: String): Long {
        return try {
            SmbFile("smb://$host/$path", ctx).length()
        } catch (_: Exception) {
            -1L
        }
    }
}
