package io.legado.app.ui.book.read.config

import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.RegexColorRule
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.read.page.provider.TextChapterLayout
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.utils.postEvent

data class RegexColorRuleCompose(
    var name: String,
    var pattern: String,
    var color: Int,
    var fontPath: String = ""
)

@Composable
fun RegexColorConfigDialogCompose(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rules = remember {
        mutableStateListOf<RegexColorRuleCompose>().apply {
            addAll(ReadBookConfig.regexColorRules.map { 
                RegexColorRuleCompose(it.name, it.pattern, it.color, it.fontPath)
            })
        }
    }
    var editingRuleIndex by remember { mutableIntStateOf(-1) }
    
    Dialog(onDismissRequest = onDismiss) {
        val cardColor = ThemeStore.backgroundCard(context)
        val textColorPrimary = ThemeStore.textColorPrimary(context)
        val textColorSecondary = ThemeStore.textColorSecondary(context)
        val accentColor = ThemeStore.accentColor(context)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(cardColor)
            )
        ) {
            Column {
                // 标题区域
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // DragHandleView
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 8.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFCCCCCC))
                    )
                    
                    Text(
                        text = "正则表达式匹配设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(textColorPrimary),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // 规则列表
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(rules) { index, rule ->
                        RuleItem(
                            rule = rule,
                            index = index,
                            textColorPrimary = textColorPrimary,
                            textColorSecondary = textColorSecondary,
                            accentColor = accentColor,
                            onColorClick = { position ->
                                editingRuleIndex = position
                                showColorPicker(context, rules, position)
                            },
                            onFontClick = { position ->
                                editingRuleIndex = position
                                // TODO: 需要实现字体选择器
                            },
                            onDeleteClick = { position ->
                                rules.removeAt(position)
                                saveRules(context, rules)
                            }
                        )
                    }
                }
                
                // 添加按钮
                OutlinedButton(
                    onClick = {
                        showAddRuleDialog(context, rules) {
                            saveRules(context, rules)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("添加规则")
                }
            }
        }
    }
}

@Composable
fun RuleItem(
    rule: RegexColorRuleCompose,
    index: Int,
    textColorPrimary: Int,
    textColorSecondary: Int,
    accentColor: Int,
    onColorClick: (Int) -> Unit,
    onFontClick: (Int) -> Unit,
    onDeleteClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(textColorPrimary).copy(alpha = 0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 标题和删除按钮行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(textColorPrimary)
                    )
                    Text(
                        text = rule.pattern,
                        fontSize = 14.sp,
                        color = Color(textColorSecondary),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                IconButton(
                    onClick = { onDeleteClick(index) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除规则",
                        tint = Color(textColorSecondary),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // 字体和颜色按钮行
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = { onFontClick(index) }
                ) {
                    Text("字体")
                }
                
                TextButton(
                    onClick = { onColorClick(index) }
                ) {
                    Text("颜色")
                }
            }
        }
    }
}

private fun showColorPicker(context: Context, rules: MutableList<RegexColorRuleCompose>, position: Int) {
    val rule = rules[position]
    val colorValue = rule.color or 0xFF000000.toInt()
    ColorPickerDialog.newBuilder()
        .setColor(colorValue)
        .setShowAlphaSlider(false)
        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
        .setDialogId(7900)
        .show(context as androidx.fragment.app.FragmentActivity)
}

private fun showAddRuleDialog(
    context: Context,
    rules: MutableList<RegexColorRuleCompose>,
    onSave: () -> Unit
) {
    val defaultPatterns = listOf(
        "\u201C匹配内容\u201D" to "\u201C.+?\u201D",
        "《匹配内容》" to "《.+?》",
        "\"匹配内容\"" to "\".+?\""
    )
    val displayItems = defaultPatterns.map { it.first } + "自定义规则"
    
    AlertDialog.Builder(context)
        .setTitle("添加正则规则")
        .setItems(displayItems.toTypedArray()) { _, i ->
            if (i < defaultPatterns.size) {
                val (name, pattern) = defaultPatterns[i]
                rules.add(RegexColorRuleCompose(name, pattern, ThemeStore.accentColor(context)))
                onSave()
            } else {
                showCustomRuleDialog(context, rules, onSave)
            }
        }
        .show()
}

private fun showCustomRuleDialog(
    context: Context,
    rules: MutableList<RegexColorRuleCompose>,
    onSave: () -> Unit
) {
    val editText = EditText(context).apply {
        hint = "输入正则表达式，如：\\u201C.+?\\u201D"
    }
    AlertDialog.Builder(context)
        .setTitle("自定义正则规则")
        .setView(editText)
        .setPositiveButton("确定") { _, _ ->
            val pattern = editText.text.toString().trim()
            if (pattern.isNotEmpty()) {
                rules.add(RegexColorRuleCompose(pattern, pattern, ThemeStore.accentColor(context)))
                onSave()
            }
        }
        .setNegativeButton("取消", null)
        .show()
}

private fun saveRules(context: Context, rules: List<RegexColorRuleCompose>) {
    ReadBookConfig.regexColorRules.clear()
    ReadBookConfig.regexColorRules.addAll(rules.map {
        RegexColorRule(it.name, it.pattern, it.color, it.fontPath)
    })
    ReadBookConfig.saveRegexColorRules()
    TextChapterLayout.invalidateRegexCache()
    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
}
