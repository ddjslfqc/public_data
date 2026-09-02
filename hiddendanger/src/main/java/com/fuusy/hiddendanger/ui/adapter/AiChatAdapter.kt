package com.fuusy.hiddendanger.ui.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.AiChatMessage
import com.fuusy.hiddendanger.data.QianwenSource

class AiChatAdapter(
    private val onToggleSources: (String) -> Unit,
    private val onCopy: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onSkipTyping: () -> Unit,
    private val onShowTiming: (AiChatMessage) -> Unit = {},
    private val onJumpToSource: (messageId: String, rank: Int) -> Unit = { _, _ -> },
    private val onOpenSourceUrl: (String) -> Unit = {}
) : ListAdapter<AiChatMessage, RecyclerView.ViewHolder>(Diff()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position).role) {
        "user" -> TYPE_USER
        else -> TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserVH(inflater.inflate(R.layout.item_ai_chat_user, parent, false))
        } else {
            AssistantVH(inflater.inflate(R.layout.item_ai_chat_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bindFull(holder, getItem(position))
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_CONTENT) && holder is AssistantVH) {
            holder.bindContentOnly(getItem(position), onSkipTyping)
        } else {
            bindFull(holder, getItem(position))
        }
    }

    private fun bindFull(holder: RecyclerView.ViewHolder, item: AiChatMessage) {
        when (holder) {
            is UserVH -> holder.bind(item)
            is AssistantVH -> holder.bind(
                item,
                onToggleSources,
                onCopy,
                onDelete,
                onSkipTyping,
                onShowTiming,
                onJumpToSource,
                onOpenSourceUrl
            )
        }
    }

    class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: TextView = view.findViewById(R.id.tv_bubble)
        private val time: TextView = view.findViewById(R.id.tv_time)
        fun bind(item: AiChatMessage) {
            val max = (itemView.resources.displayMetrics.widthPixels * 0.72f).toInt()
            bubble.maxWidth = max
            bubble.text = item.content
            time.text = item.time
        }
    }

    class AssistantVH(view: View) : RecyclerView.ViewHolder(view) {
        private val bubble: TextView = view.findViewById(R.id.tv_bubble)
        private val time: TextView = view.findViewById(R.id.tv_time)
        private val sourcesChip: TextView = view.findViewById(R.id.tv_sources)
        private val sourcesPanel: LinearLayout = view.findViewById(R.id.ll_sources)
        private val tvWebSearch: TextView? = view.findViewById(R.id.tv_web_search)
        private val tvTiming: TextView? = view.findViewById(R.id.tv_timing)
        private val btnCopy: TextView = view.findViewById(R.id.btn_copy)
        private val btnDelete: TextView = view.findViewById(R.id.btn_delete)
        private val tvHint: TextView? = view.findViewById(R.id.tv_typing_hint)

        fun bindContentOnly(item: AiChatMessage, onSkipTyping: () -> Unit) {
            val thinking = item.role == "thinking"
            val typing = item.isTyping
            bubble.movementMethod = null
            bubble.text = when {
                thinking -> formatThinkingText(item.content)
                typing -> item.content + "▍"
                else -> item.content
            }
            tvHint?.isVisible = typing
            tvHint?.text = "轻触气泡 · 跳过动画"
            if (typing) {
                bubble.setOnClickListener { onSkipTyping() }
            } else {
                bubble.setOnClickListener(null)
                bubble.isClickable = false
            }
        }

        fun bind(
            item: AiChatMessage,
            onToggleSources: (String) -> Unit,
            onCopy: (String) -> Unit,
            onDelete: (String) -> Unit,
            onSkipTyping: () -> Unit,
            onShowTiming: (AiChatMessage) -> Unit,
            onJumpToSource: (String, Int) -> Unit,
            onOpenSourceUrl: (String) -> Unit
        ) {
            val max = (itemView.resources.displayMetrics.widthPixels * 0.78f).toInt()
            bubble.maxWidth = max

            val thinking = item.role == "thinking"
            val typing = item.isTyping
            val body = when {
                thinking -> formatThinkingText(item.content)
                typing -> item.content + "▍"
                else -> item.content
            }
            if (!thinking && !typing && item.sources.isNotEmpty()) {
                bindCitationText(bubble, body.toString(), item, onJumpToSource)
            } else {
                bubble.movementMethod = null
                bubble.text = body
            }
            time.text = item.time
            time.isVisible = !thinking
            tvWebSearch?.isVisible = !thinking && !typing && item.webSearchUsed
            btnCopy.isVisible = !thinking && !typing && item.content.isNotBlank()
            btnDelete.isVisible = !thinking && !typing
            tvHint?.isVisible = typing
            tvHint?.text = "轻触气泡 · 跳过动画"
            btnCopy.setOnClickListener {
                onCopy(item.fullContent.ifBlank { item.content })
            }
            btnDelete.setOnClickListener { onDelete(item.id) }
            bubble.setTextColor(
                if (thinking) 0xFF686D79.toInt() else 0xFF111827.toInt()
            )
            if (typing) {
                bubble.setOnClickListener { onSkipTyping() }
            } else {
                bubble.setOnClickListener(null)
                bubble.isClickable = false
            }

            val totalMs = item.totalMs
            val showTiming = !thinking && !typing && totalMs != null && totalMs >= 0
            tvTiming?.isVisible = showTiming
            if (showTiming && tvTiming != null) {
                tvTiming.text = "耗时 ${formatDuration(totalMs)}"
                tvTiming.setOnClickListener { onShowTiming(item) }
            } else {
                tvTiming?.setOnClickListener(null)
            }

            val hasSources = item.shouldShowSources()
            sourcesChip.isVisible = hasSources
            if (hasSources) {
                sourcesChip.text = "引用 ${item.sources.size} 个来源"
                sourcesChip.setOnClickListener { onToggleSources(item.id) }
            }
            sourcesPanel.isVisible = hasSources && item.sourcesExpanded
            if (sourcesPanel.isVisible) {
                sourcesPanel.removeAllViews()
                val density = itemView.resources.displayMetrics.density
                item.sources.take(8).forEachIndexed { index, src ->
                    val rank = sourceRank(src, index)
                    val highlighted = item.highlightSourceRank == rank
                    val row = LinearLayout(itemView.context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(
                            (12 * density).toInt(),
                            (10 * density).toInt(),
                            (12 * density).toInt(),
                            (10 * density).toInt()
                        )
                        if (highlighted) {
                            setBackgroundResource(R.drawable.bg_ai_source_highlight)
                        }
                        tag = rank
                    }
                    val head = LinearLayout(itemView.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    val rankTv = TextView(itemView.context).apply {
                        text = rank.toString()
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(0xFF1465EB.toInt())
                        gravity = android.view.Gravity.CENTER
                        setBackgroundResource(R.drawable.bg_ai_source_rank)
                        layoutParams = LinearLayout.LayoutParams(
                            (20 * density).toInt(),
                            (20 * density).toInt()
                        )
                    }
                    val titleTv = TextView(itemView.context).apply {
                        text = src.title?.ifBlank { "来源" } ?: "来源"
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (highlighted) 0xFF1465EB.toInt() else 0xFF111827.toInt())
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply { marginStart = (8 * density).toInt() }
                    }
                    head.addView(rankTv)
                    head.addView(titleTv)
                    row.addView(head)
                    val excerpt = src.text.orEmpty().trim()
                    if (excerpt.isNotEmpty()) {
                        row.addView(
                            TextView(itemView.context).apply {
                                text = excerpt.take(160)
                                textSize = 12f
                                setTextColor(0xFF5F6E80.toInt())
                                setPadding((28 * density).toInt(), (6 * density).toInt(), 0, 0)
                            }
                        )
                    }
                    val meta = listOfNotNull(
                        src.spaceName?.takeIf { it.isNotBlank() },
                        src.updatedAt?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        row.addView(
                            TextView(itemView.context).apply {
                                text = meta
                                textSize = 11f
                                setTextColor(0xFF8B93A7.toInt())
                                setPadding((28 * density).toInt(), (4 * density).toInt(), 0, 0)
                            }
                        )
                    }
                    val url = src.sourceUrl?.trim().orEmpty()
                    if (url.isNotBlank()) {
                        row.addView(
                            TextView(itemView.context).apply {
                                text = "查看原文"
                                textSize = 12f
                                setTextColor(0xFF1465EB.toInt())
                                setPadding((28 * density).toInt(), (6 * density).toInt(), 0, 0)
                                setOnClickListener { onOpenSourceUrl(url) }
                            }
                        )
                    }
                    sourcesPanel.addView(row)
                }
                item.highlightSourceRank?.let { target ->
                    sourcesPanel.post {
                        val child = (0 until sourcesPanel.childCount)
                            .map { sourcesPanel.getChildAt(it) }
                            .firstOrNull { it.tag == target }
                        child?.requestFocus()
                    }
                }
            }
        }

        private fun formatThinkingText(raw: String): CharSequence {
            val text = raw.ifBlank { "正在思考" }
            val nl = text.indexOf('\n')
            if (nl < 0 || nl >= text.lastIndex) return text
            val builder = SpannableStringBuilder(text)
            builder.setSpan(
                RelativeSizeSpan(0.78f),
                nl + 1,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return builder
        }

        private fun bindCitationText(
            bubble: TextView,
            body: String,
            item: AiChatMessage,
            onJumpToSource: (String, Int) -> Unit
        ) {
            val ranks = item.sources.mapIndexed { i, s -> sourceRank(s, i) }.toSet()
            val builder = SpannableStringBuilder()
            var last = 0
            CITATION_REGEX.findAll(body).forEach { match ->
                val rank = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toIntOrNull()
                builder.append(body, last, match.range.first)
                if (rank != null && ranks.contains(rank)) {
                    val label = "【$rank】"
                    val start = builder.length
                    builder.append(label)
                    val end = builder.length
                    builder.setSpan(
                        object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                onJumpToSource(item.id, rank)
                            }

                            override fun updateDrawState(ds: TextPaint) {
                                ds.isUnderlineText = false
                                ds.color = 0xFF2563EB.toInt()
                                ds.typeface = Typeface.DEFAULT_BOLD
                            }
                        },
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        RelativeSizeSpan(0.72f),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        SuperscriptSpan(),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    builder.append(match.value)
                }
                last = match.range.last + 1
            }
            if (last < body.length) builder.append(body, last, body.length)
            bubble.movementMethod = LinkMovementMethod.getInstance()
            bubble.highlightColor = Color.TRANSPARENT
            bubble.text = builder
        }
    }

    class Diff : DiffUtil.ItemCallback<AiChatMessage>() {
        override fun areItemsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: AiChatMessage, newItem: AiChatMessage) =
            oldItem == newItem

        override fun getChangePayload(oldItem: AiChatMessage, newItem: AiChatMessage): Any? {
            if (oldItem.id != newItem.id) return null
            if (oldItem.content != newItem.content &&
                oldItem.role == newItem.role &&
                oldItem.isTyping == newItem.isTyping &&
                oldItem.sources == newItem.sources &&
                oldItem.sourcesExpanded == newItem.sourcesExpanded &&
                oldItem.highlightSourceRank == newItem.highlightSourceRank
            ) {
                return PAYLOAD_CONTENT
            }
            return null
        }
    }

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_ASSISTANT = 2
        const val PAYLOAD_CONTENT = "content"

        /** 对齐 Web：【资料 N】/[资料 N]；并兼容正文里的 [1]/【1】角标（截图样式） */
        private val CITATION_REGEX =
            Regex("""\[资料\s*(\d+)\]|【资料\s*(\d+)】|【(\d{1,2})】|\[(\d{1,2})\]""")

        fun sourceRank(source: QianwenSource, index: Int): Int =
            source.rank?.takeIf { it > 0 } ?: (index + 1)

        private val TIMING_LABELS = listOf(
            "standard_qa_ms" to "规范问答匹配",
            "parallel_retrieval_ms" to "并行召回总耗时",
            "local_candidate_retrieval_ms" to "本地候选召回（并行子项）",
            "web_search_ms" to "联网搜索（并行子项）",
            "query_analysis_ms" to "问题分析",
            "dense_ms" to "Dense 检索",
            "bm25_ms" to "BM25 检索",
            "fusion_ms" to "RRF 融合",
            "reranker_ms" to "Reranker 排序",
            "relevance_gate_ms" to "相关性过滤",
            "dedup_ms" to "文档去重",
            "context_expansion_ms" to "父章节恢复",
            "evidence_check_ms" to "证据充分性检查",
            "retry_retrieval_ms" to "补充检索",
            "retry_evidence_check_ms" to "补充证据检查",
            "retrieval_total_ms" to "检索准备总计",
            "first_token_ms" to "首字等待",
            "generation_ms" to "回答生成",
            "total_ms" to "总耗时"
        )

        fun formatDuration(ms: Double?): String {
            if (ms == null || !ms.isFinite() || ms < 0) return "—"
            val sec = ms / 1000.0
            return if (ms < 10_000) String.format("%.2fs", sec) else String.format("%.1fs", sec)
        }

        data class TimingRow(val key: String, val label: String, val value: String)

        /** 与 Web timingRows 一致：按固定标签顺序，只展示有数值的阶段 */
        fun buildTimingRows(item: AiChatMessage): List<TimingRow> {
            val values = item.timings.toMutableMap()
            val total = item.totalMs
            if (total != null && total.isFinite() && !values.containsKey("total_ms")) {
                values["total_ms"] = total
            }
            val rows = TIMING_LABELS
                .filter { (key, _) -> values[key]?.isFinite() == true }
                .map { (key, label) -> TimingRow(key, label, formatDuration(values[key])) }
            if (rows.isNotEmpty()) return rows
            return listOf(TimingRow("total_ms", "总耗时", formatDuration(total)))
        }

        fun buildTimingDetail(item: AiChatMessage): String =
            buildTimingRows(item).joinToString("\n") { "${it.label}  ${it.value}" }
    }
}
