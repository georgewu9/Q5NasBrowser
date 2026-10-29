package com.example.q5nasbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        recycler.adapter = FileAdapter(items, onItem)
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

    private fun playFile(host: String, path: String) {
        val (user, pass) = SmbConfig.load(this)
        scope.launch {
            try {
                val ctx = withContext(Dispatchers.IO) { SmbConfig.buildContext(user, pass) }
                proxy?.stop()
                val port = findFreePort()
                proxy = ProxyServer(port, ctx, host, path).apply { start() }
                // URL 保留真实文件名后缀（如 .mkv），便于播放器识别格式/选解码器；
                // 真正读取由代理按内部保存的 path 完成，request URI 本身不参与查找
                val uri = Uri.parse("http://127.0.0.1:$port/$path")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setDataAndType(uri, "video/*")
                }
                startActivity(Intent.createChooser(intent, "选择播放器播放"))
            } catch (e: Exception) {
                statusText.text = "播放失败：${e.message}"
            }
        }
    }

    private fun findFreePort(): Int {
        val s = ServerSocket(0)
        val p = s.localPort
        s.close()
        return p
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (stack.size > 1) {
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
