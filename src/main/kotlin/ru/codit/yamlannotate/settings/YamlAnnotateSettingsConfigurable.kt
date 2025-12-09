package ru.codit.yamlannotate.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.ColorPanel
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class YamlAnnotateSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private var colorPanel: ColorPanel? = null

    override fun getDisplayName(): String = "YAML Annotate"

    override fun createComponent(): JComponent {
        val root = JPanel(BorderLayout(8, 8))
        val label = JLabel("Цвет inline-текста:")
        val cp = ColorPanel()
        // Разрешаем редактирование цвета
        cp.setEditable(true)
        cp.selectedColor = YamlAnnotateSettings.getInstance().getInlineColor()

        val inner = JPanel(BorderLayout(8, 8))
        inner.add(label, BorderLayout.WEST)
        inner.add(cp, BorderLayout.CENTER)
        root.add(inner, BorderLayout.NORTH)

        panel = root
        colorPanel = cp
        return root
    }

    override fun isModified(): Boolean {
        val current = YamlAnnotateSettings.getInstance().getInlineColor()
        val sel = colorPanel?.selectedColor ?: DEFAULT_COLOR
        return !colorsEqual(current, sel)
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val sel = colorPanel?.selectedColor ?: return
        YamlAnnotateSettings.getInstance().setInlineColor(sel)
    }

    override fun reset() {
        colorPanel?.selectedColor = YamlAnnotateSettings.getInstance().getInlineColor()
    }

    override fun disposeUIResources() {
        panel = null
        colorPanel = null
    }

    private fun colorsEqual(a: Color, b: Color): Boolean = a.rgb == b.rgb

    companion object {
        private val DEFAULT_COLOR = Color(0x00, 0xAA, 0x00)
    }
}
