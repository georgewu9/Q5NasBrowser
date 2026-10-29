package com.example.q5nasbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 简单列表适配器（电视盒遥控优化）：
 * - 由 Activity 统一用方向键控制 selectedPosition 高亮（圆角白框）。
 * - 文件夹 = 进入下一层；文件 = 播放。
 * - 单击仍保留（触屏/鼠标可用）。
 */
class FileAdapter(
    private val items: List<FileItem>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    var selectedPosition = 0

    class VH(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false) as TextView
        // 不让子项自动抢焦点，方向键导航完全由 Activity 接管
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val prefix = when {
            item.isDirectory -> "📁 "
            else -> when (MediaType.kindOf(item.path)) {
                MediaType.Kind.VIDEO -> "🎬 "
                MediaType.Kind.AUDIO -> "🎵 "
                MediaType.Kind.IMAGE -> "🖼 "
                MediaType.Kind.OTHER -> "📄 "
            }
        }
        holder.view.text = prefix + item.name
        val ctx = holder.view.context
        holder.view.background = if (position == selectedPosition)
            ctx.getDrawable(R.drawable.bg_item_selected)
        else
            ctx.getDrawable(R.drawable.bg_item_normal)
        holder.view.setOnClickListener { onClick(item) }
    }

    /** 仅刷新变更的两项，避免整列表重绘闪烁。 */
    fun setSelected(pos: Int) {
        if (pos == selectedPosition) return
        val old = selectedPosition
        selectedPosition = pos
        notifyItemChanged(old)
        notifyItemChanged(pos)
    }

    override fun getItemCount(): Int = items.size
}
