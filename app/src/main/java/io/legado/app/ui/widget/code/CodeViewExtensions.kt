@file:Suppress("unused")

package io.legado.app.ui.widget.code

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import io.legado.app.lib.theme.ThemeStore
import splitties.init.appCtx
import java.util.regex.Pattern

val legadoPattern: Pattern = Pattern.compile("\\|\\||&&|%%|@js:|@Json:|@css:|@@|@XPath:|@webjs:")
val jsonPattern: Pattern = Pattern.compile("\"[A-Za-z0-9]*?\"\\:|\"|\\{|\\}|\\[|\\]")
val wrapPattern: Pattern = Pattern.compile("\\\\n")
val operationPattern: Pattern =
    Pattern.compile(":|==|>|<|!=|>=|<=|->|=|%|-|-=|%=|\\+|\\-|\\-=|\\+=|\\^|\\&|\\|::|\\?|\\*")
val jsPattern: Pattern = Pattern.compile("\\b(?:var|let|const)\\b")

fun CodeView.addLegadoPattern() {
    addSyntaxPattern(legadoPattern, ThemeStore.accentColor(context))
}

fun CodeView.addJsonPattern() {
    addSyntaxPattern(jsonPattern, ThemeStore.accentColor(context))
}

fun CodeView.addJsPattern() {
    addSyntaxPattern(wrapPattern, ThemeStore.textColorSecondary(context))
    addSyntaxPattern(operationPattern, ThemeStore.accentColor(context))
    addSyntaxPattern(jsPattern, ThemeStore.accentColor(context))
}

fun Context.arrayAdapter(keywords: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, R.layout.item_1line_text_and_del, R.id.text_view, keywords)
}