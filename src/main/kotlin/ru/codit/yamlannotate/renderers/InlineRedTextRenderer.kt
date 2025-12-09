package ru.codit.yamlannotate.renderers

import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import ru.codit.yamlannotate.settings.YamlAnnotateSettings
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Рендерер для inline-подсказки красного цвета.
 * Текст нередактируемый, отображается после указанного офсета в первой строке.
 */
class InlineRedTextRenderer(private val text: String) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val editor = inlay.editor
        val font = editor.colorsScheme.getFont(EditorFontType.BOLD)
        val fm = editor.contentComponent.getFontMetrics(font)
        return fm.stringWidth(text)
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes
    ) {
        val editor = inlay.editor
        val font = editor.colorsScheme.getFont(EditorFontType.BOLD)
        g.font = font
        // Цвет берём из настроек плагина
        g.color = YamlAnnotateSettings.getInstance().getInlineColor()

        val fm = g.fontMetrics
        val baseline = targetRegion.y + targetRegion.height - fm.descent
        g.drawString(text, targetRegion.x, baseline)
    }
}
