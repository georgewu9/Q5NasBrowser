package com.example.q5nasbrowser

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 简单列表适配器。适配电视盒遥控：
 * 单击（或遥控 OK / 方向键中心键）即触发 onItem。
 * 文件夹 = 进入下一层；文件 = 播放。
 */
class FileAdapter(
    private val items: List<FileItem>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        tv.textSize = 18f
        tv.setPadding(24, 18, 24, 18)
        tv.isFocusable = true
        tv.isFocusableInTouchMode = true
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val prefix = if (item.isDirectory) "[目录] " else "[文件] "
        holder.view.text = prefix + item.name
        holder.view.setOnClickListener { onClick(item) }
        holder.view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                onClick(item)
                true
            } else {
                false
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
