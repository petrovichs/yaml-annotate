package ru.codit.yamlannotate.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.awt.Color

@State(name = "YamlAnnotateSettings", storages = [Storage("yaml-annotate.xml")])
@Service(Service.Level.APP)
class YamlAnnotateSettings : PersistentStateComponent<YamlAnnotateSettings.State> {

    data class State(
        var inlineColorHex: String = DEFAULT_COLOR_HEX
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun getInlineColor(): Color = parseColor(state.inlineColorHex) ?: parseColor(DEFAULT_COLOR_HEX)!!

    fun setInlineColor(color: Color) {
        state.inlineColorHex = toHex(color)
    }

    companion object {
        private const val DEFAULT_COLOR_HEX = "#00AA00" // зелёный по умолчанию

        fun getInstance(): YamlAnnotateSettings = service()

        private fun parseColor(hex: String?): Color? {
            if (hex.isNullOrBlank()) return null
            val s = hex.trim()
            return try {
                Color.decode(s)
            } catch (_: Throwable) {
                null
            }
        }

        private fun toHex(color: Color): String {
            val r = color.red
            val g = color.green
            val b = color.blue
            return String.format("#%02X%02X%02X", r, g, b)
        }
    }
}
