package com.example.q5nasbrowser

import android.content.Context
import android.content.SharedPreferences
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import java.util.Properties

/**
 * 读取/保存 NAS 账号，并构造 jcifs-ng 的 CIFSContext。
 * 用户名为空时表示匿名/访客访问。
 */
object SmbConfig {

    private const val PREFS = "q5nas_prefs"
    private const val KEY_USER = "username"
    private const val KEY_PASS = "password"

    fun load(context: Context): Pair<String, String> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val user = sp.getString(KEY_USER, "") ?: ""
        val pass = sp.getString(KEY_PASS, "") ?: ""
        return user to pass
    }

    fun save(context: Context, user: String, pass: String) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_USER, user).putString(KEY_PASS, pass).apply()
    }

    /**
     * 构造 SMB 上下文：允许 SMB1 ~ SMB3.1.1 协商，
     * 这样既能连老设备的 SMB1，也能连 WD My Cloud Home 这类只给 SMB2/3 的设备。
     */
    fun buildContext(user: String, pass: String): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.connTimeout", "5000")
        }
        val base: CIFSContext = BaseContext(PropertyConfiguration(props))
        return if (user.isBlank()) {
            base
        } else {
            base.withCredentials(NtlmPasswordAuthenticator(user, pass))
        }
    }
}
