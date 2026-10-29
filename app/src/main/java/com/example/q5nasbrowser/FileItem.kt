package com.example.q5nasbrowser

/**
 * 列表中的一项：可以是发现的 NAS 主机、共享目录、或目录/文件。
 * @param name     显示名称（IP 或 共享名 或 文件名）
 * @param isDirectory 是否为目录（true 可进入下一层；false 为文件，点击播放）
 * @param path     smb 路径中 host 之后的部分，例如 "share" 或 "share/电影/2024"
 * @param host     NAS 的 IP 地址
 */
data class FileItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String,
    val host: String
)
