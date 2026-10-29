package com.example.q5nasbrowser

/**
 * 扩展名 → MIME 与媒体大类。
 *
 * 代理层（设置 Content-Type）与 UI 层（选播放器/看图软件、列表图标）共用同一张表，
 * 避免两处各写一份映射导致不一致。
 */
object MediaType {

    enum class Kind { VIDEO, AUDIO, IMAGE, OTHER }

    fun kindOf(path: String): Kind = when (ext(path)) {
        in VIDEO_EXTS -> Kind.VIDEO
        in AUDIO_EXTS -> Kind.AUDIO
        in IMAGE_EXTS -> Kind.IMAGE
        else -> Kind.OTHER
    }

    fun mimeOf(path: String): String = when (ext(path)) {
        // 视频
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "ts", "m2ts", "mts" -> "video/mp2t"
        "avi", "divx" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "rmvb", "rm" -> "video/vnd.rn-realvideo"
        "mpg", "mpeg" -> "video/mpeg"
        "3gp" -> "video/3gpp"
        "webm" -> "video/webm"

        // 音频
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/x-wav"
        "aac" -> "audio/aac"
        "m4a", "alac" -> "audio/mp4"
        "ape" -> "audio/x-ape"
        "ogg", "oga", "opus" -> "audio/ogg"
        "wma" -> "audio/x-ms-wma"
        "aif", "aiff" -> "audio/x-aiff"

        // 图片
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "tif", "tiff" -> "image/tiff"

        else -> "application/octet-stream"
    }

    /** 取小写扩展名；无扩展名返回空串。 */
    private fun ext(path: String): String {
        val name = path.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot >= 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    private val VIDEO_EXTS = setOf(
        "mkv", "mp4", "m4v", "ts", "m2ts", "mts", "avi", "divx", "mov",
        "flv", "wmv", "rmvb", "rm", "mpg", "mpeg", "3gp", "webm"
    )
    private val AUDIO_EXTS = setOf(
        "mp3", "flac", "wav", "aac", "m4a", "alac", "ape",
        "ogg", "oga", "opus", "wma", "aif", "aiff"
    )
    private val IMAGE_EXTS = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "tif", "tiff"
    )
}
