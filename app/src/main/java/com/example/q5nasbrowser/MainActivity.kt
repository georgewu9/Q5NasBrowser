package com.example.q5nasbrowser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket

class MainActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var scanBtn: Button
    private lateinit var settingsBtn: Button

    private enum class Kind { ROOT, SHARES, FILES }

    private data class NavState(
        val kind: Kind,
        val host: String = "",
        val path: String = "",
        val items: List<FileItem> = emptyList()
    )

    private val stack = mutableListOf<NavState>()
    private var proxy: ProxyServer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 遥控方向键导航状态
    private var selectedIndex = 0
    private var currentAdapter: FileAdapter? = null
    private var currentOnItem: ((FileItem) -> Unit)? = null
    private var currentItems: List<FileItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        statusText = findViewById(R.id.statusText)
        scanBtn = findViewById(R.id.scanBtn)
        settingsBtn = findViewById(R.id.settingsBtn)

        recycler.layoutManager = LinearLayoutManager(this)

        scanBtn.setOnClickListener { startScan() }
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        statusText.text = "点击“扫描局域网NAS”开始查找设备"
        render(emptyList()) {}
    }

    private fun render(items: List<FileItem>, onItem: (FileItem) -> Unit) {
        currentItems = items
        currentOnItem = onItem
        selectedIndex = 0
        val adapter = FileAdapter(items, onItem)
        currentAdapter = adapter
        recycler.adapter = adapter
        if (items.isNotEmpty()) {
            recycler.scrollToPosition(0)
            recycler.requestFocus()
        }
    }

    private fun startScan() {
        val (user, pass) = SmbConfig.load(this)
        progress.visibility = View.VISIBLE
        statusText.text = "正在扫描局域网 SMB 设备 ..."
        scope.launch {
            val hosts = withContext(Dispatchers.IO) {
                LanScanner.scan()
            }
            progress.visibility = View.GONE
            if (hosts.isEmpty()) {
                statusText.text = "未扫描到 SMB 设备，请确认 NAS 已开机并与本机在同一网段"
            } else {
                statusText.text = "发现 ${hosts.size} 台设备，点击进入"
                val items = hosts.map {
                    FileItem(name = it.ip, isDirectory = true, path = it.ip, host = it.ip)
                }
                stack.clear()
                stack.add(NavState(Kind.ROOT, items = items))
                render(items) { item -> openHost(item.host) }
            }
        }
    }

    private fun openHost(host: String) {
        val (user, pass) = SmbConfig.load(this)
        progress.visibility = View.VISIBLE
        statusText.text = "正在连接 $host ..."
        scope.launch {
            try {
                val ctx = withContext(Dispatchers.IO) { SmbConfig.buildContext(user, pass) }
                val shares = withContext(Dispatchers.IO) { SmbBrowser.listShares(ctx, host) }
                progress.visibility = View.GONE
                if (shares.isEmpty()) {
                    statusText.text = "$host 未发现共享，可能需正确账号密码，请到“设置账号”填写"
                } else {
                    statusText.text = "$host - 选择共享目录"
                    stack.add(NavState(Kind.SHARES, host = host, items = shares))
                    render(shares) { item -> openPath(host, item.path) }
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                statusText.text = "连接 $host 失败：${e.message}"
            }
        }
    }

    private fun openPath(host: String, path: String) {
        val (user, pass) = SmbConfig.load(this)
        progress.visibility = View.VISIBLE
        statusText.text = "正在加载 $path ..."
        scope.launch {
            try {
                val ctx = withContext(Dispatchers.IO) { SmbConfig.buildContext(user, pass) }
                val files = withContext(Dispatchers.IO) { SmbBrowser.listFiles(ctx, host, path) }
                progress.visibility = View.GONE
                statusText.text = path
                stack.add(NavState(Kind.FILES, host = host, path = path, items = files))
                render(files) { item ->
                    if (item.isDirectory) openPath(host, item.path)
                    else playFile(host, item.path)
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                statusText.text = "加载失败：${e.message}"
            }
        }
    }

    /** 点击文件：按媒体类型分流到视频/音频（HTTP 代理）或图片（下载后本地打开）。 */
    private fun playFile(host: String, path: String) {
        when (MediaType.kindOf(path)) {
            MediaType.Kind.VIDEO -> streamAndOpen(host, path, MediaType.Kind.VIDEO)
            MediaType.Kind.AUDIO -> streamAndOpen(host, path, MediaType.Kind.AUDIO)
            MediaType.Kind.IMAGE -> openImage(host, path)
            MediaType.Kind.OTHER -> {
                val name = path.substringAfterLast('/')
                statusText.text = "不支持的类型：$name"
                Toast.makeText(this, "不是可播放的音视频或图片：$name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 视频/音频：走本地 HTTP 代理，支持 Range 拖动进度。 */
    private fun streamAndOpen(host: String, path: String, kind: MediaType.Kind) {
        val (user, pass) = SmbConfig.load(this)
        val mime = MediaType.mimeOf(path)
        statusText.text = "正在准备：${path.substringAfterLast('/')}"
        scope.launch {
            try {
                val ctx = withContext(Dispatchers.IO) { SmbConfig.buildContext(user, pass) }
                proxy?.stop()
                val port = findFreePort()
                proxy = ProxyServer(port, ctx, host, path).apply { start() }
                // URL 保留真实文件名后缀（如 .mkv/.flac），便于播放器识别格式/选解码器；
                // 真正读取由代理按内部保存的 path 完成，request URI 本身不参与查找
                val uri = Uri.parse("http://127.0.0.1:$port/$path")
                val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime) }
                val title = if (kind == MediaType.Kind.AUDIO) "选择音乐播放器" else "选择播放器播放"
                launchExternal(intent, title)
            } catch (e: Exception) {
                statusText.text = "播放失败：${e.message}"
            }
        }
    }

    /**
     * 图片：看图软件普遍不支持 http 流，先下载到应用外部缓存目录，再用 file:// 打开。
     *
     * - API 22 上 targetSdk 也是 22，file:// 不受 FileUriExposed 限制，无需 FileProvider。
     * - 用 getExternalCacheDir() 而非内部 cacheDir：内部目录属于本应用 uid，
     *   其它看图软件读不到；外部缓存目录在 Android 11 之前任何应用都可读，且写入不需要权限。
     */
    private fun openImage(host: String, path: String) {
        val (user, pass) = SmbConfig.load(this)
        val name = path.substringAfterLast('/')
        val mime = MediaType.mimeOf(path)
        statusText.text = "正在加载图片：$name"
        progress.visibility = View.VISIBLE
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val ctx = SmbConfig.buildContext(user, pass)
                    val dir = imageCacheDir()
                    pruneImageCache(dir)
                    // 文件名里的空格/特殊字符会让部分看图软件解析失败，统一替换
                    val safe = name.replace(Regex("[^0-9A-Za-z.\\-\\u4e00-\\u9fa5]"), "_")
                    val outFile = File(dir, safe)
                    SmbBrowser.openStream(ctx, host, path).use { input ->
                        FileOutputStream(outFile).use { os -> input.copyTo(os, 64 * 1024) }
                    }
                    outFile
                }
                progress.visibility = View.GONE
                statusText.text = path
                val uri = Uri.fromFile(file)
                val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime) }
                launchExternal(intent, "选择看图软件")
            } catch (e: Exception) {
                progress.visibility = View.GONE
                statusText.text = "图片加载失败：${e.message}"
            }
        }
    }

    /** 图片缓存目录：优先外部缓存（其它应用可读），拿不到时退回内部缓存。 */
    private fun imageCacheDir(): File {
        val base = getExternalCacheDir() ?: cacheDir
        return File(base, "preview").apply { mkdirs() }
    }

    /** 简单清理：删除 7 天前的图片缓存，避免长期占用空间。 */
    private fun pruneImageCache(dir: File) {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) f.delete()
        }
    }

    /**
     * 统一的外部跳转兜底。
     * 电视盒可能没装能打开该类型的应用，createChooser 本身不会抛异常（只会显示空列表），
     * 所以先 resolveActivity 判断，再弹选择器。
     */
    private fun launchExternal(intent: Intent, title: String) {
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "本机没有可打开该类型文件的应用", Toast.LENGTH_LONG).show()
            statusText.text = "本机没有可打开该类型文件的应用（可能未装看图/音乐软件）"
            return
        }
        try {
            startActivity(Intent.createChooser(intent, title))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "本机没有可打开该类型文件的应用", Toast.LENGTH_LONG).show()
            statusText.text = "本机没有可打开该类型文件的应用"
        }
    }

    private fun findFreePort(): Int {
        val s = ServerSocket(0)
        val p = s.localPort
        s.close()
        return p
    }

    /** 返回上一层（遥控 LEFT 键与 BACK 键共用）。 */
    private fun goBack() {
        if (stack.size <= 1) return
        stack.removeAt(stack.size - 1)
        val prev = stack.last()
        statusText.text = when (prev.kind) {
            Kind.ROOT -> "发现 ${prev.items.size} 台设备，点击进入"
            Kind.SHARES -> "${prev.host} - 选择共享目录"
            Kind.FILES -> prev.path
        }
        val cb: (FileItem) -> Unit = when (prev.kind) {
            Kind.ROOT -> { it -> openHost(it.host) }
            Kind.SHARES -> { it -> openPath(prev.host, it.path) }
            Kind.FILES -> { it ->
                if (it.isDirectory) openPath(prev.host, it.path) else playFile(prev.host, it.path)
            }
        }
        render(prev.items, cb)
    }

    /** 上下移动选中项，并滚动使目标可见（一屏显示不下时实现翻屏）。 */
    private fun moveSelection(delta: Int) {
        if (currentItems.isEmpty()) return
        val next = (selectedIndex + delta).coerceIn(0, currentItems.size - 1)
        if (next == selectedIndex) return
        selectedIndex = next
        currentAdapter?.setSelected(selectedIndex)
        recycler.scrollToPosition(selectedIndex)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 正在浏览列表时，方向键由本 Activity 接管，保证光标移动/翻屏可控
        if (stack.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> { moveSelection(1); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { moveSelection(-1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    currentItems.getOrNull(selectedIndex)?.let { currentOnItem?.invoke(it) }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    goBack()
                    return true
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (stack.size > 1) {
                goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        proxy?.stop()
        scope.cancel()
    }
}
