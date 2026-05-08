package io.legado.app.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityAiLogBinding
import io.legado.app.help.ai.AiLogManager
import kotlinx.coroutines.launch

import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * AI日志查看页面
 */
class AiLogActivity : BaseActivity<ActivityAiLogBinding>() {

    override val binding by viewBinding(ActivityAiLogBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        loadLogs()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.ai_log_menu, menu)
        return true
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_refresh -> {
                loadLogs()
                true
            }
            R.id.menu_clear -> {
                showClearConfirmDialog()
                true
            }
            R.id.menu_copy -> {
                copyLogsToClipboard()
                true
            }
            R.id.menu_export -> {
                exportLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val logs = AiLogManager.getLogs()
            runOnUiThread {
                binding.tvLogs.text = logs
                binding.scrollView.post {
                    binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                }
                updateLogInfo()
            }
        }
    }

    private fun updateLogInfo() {
        val fileSize = AiLogManager.getLogFileSize()
        val sizeStr = formatFileSize(fileSize)
        binding.tvLogInfo.text = "日志大小: $sizeStr"
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.2f MB", size / (1024.0 * 1024))
        }
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("清空日志")
            .setMessage("确定要清空所有AI日志吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                AiLogManager.clearLogs()
                loadLogs()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyLogsToClipboard() {
        val logs = binding.tvLogs.text.toString()
        if (logs.isEmpty() || logs == "暂无日志") {
            Toast.makeText(this, "没有可复制的日志", Toast.LENGTH_SHORT).show()
            return
        }
        
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI日志", logs)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun exportLogs() {
        // TODO: 实现导出功能，可以将日志文件分享到其他应用
        Toast.makeText(this, "导出功能开发中", Toast.LENGTH_SHORT).show()
    }
}
