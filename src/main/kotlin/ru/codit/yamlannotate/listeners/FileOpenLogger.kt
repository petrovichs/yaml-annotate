package ru.codit.yamlannotate.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import ru.codit.yamlannotate.renderers.InlineRedTextRenderer

/**
 * Логирует в консоль имя и содержимое файла при его открытии в IDE (песочнице).
 */
class FileOpenLogger : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        try {
            val name = file.name
            val length = file.length

            // Ограничим печать очень больших файлов, чтобы не забивать консоль
            val maxBytesToPrint = 1_000_000 // ~1 МБ

            val text: String = if (length in 1..maxBytesToPrint) {
                // Надежно определяем текст через VfsUtilCore
                VfsUtilCore.loadText(file)
            } else if (length == 0L) {
                ""
            } else {
                "<Содержимое опущено: файл слишком большой (${length} байт)>"
            }

            if (text.startsWith("<Содержимое опущено:")) {
                println("[FileOpenLogger] Открылся файл: $name\n$text")
            } else {
                println("[FileOpenLogger] Открылся файл: $name\n==== Содержимое начала ====")
                println(text)
                println("==== Содержимое конца ====")
            }

            // Добавляем inline-подсказку в конец первой строки для YAML-файлов
            val ext = file.extension?.lowercase()
            val isYaml = ext == "yml" || ext == "yaml"
            if (isYaml) {
                source.getEditors(file).forEach { fe ->
                    if (fe is TextEditor) {
                        val editor = fe.editor
                        val doc = editor.document

                        // Добавляем подсказки рядом с элементами enum на основе x-enum-varnames
                        try {
                            val text = doc.text
                            val hints = collectEnumVarnameHints(text)
                            hints.forEach { (lineIndex, varName) ->
                                if (lineIndex in 0 until doc.lineCount) {
                                    val lineEnd = doc.getLineEndOffset(lineIndex)
                                    val exists = editor.inlayModel
                                        .getInlineElementsInRange(lineEnd, lineEnd)
                                        .any { it.renderer is InlineRedTextRenderer }
                                    if (!exists) {
                                        editor.inlayModel.addInlineElement(
                                            lineEnd,
                                            true,
                                            InlineRedTextRenderer(" | $varName")
                                        )
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            LOG.warn("Не удалось добавить inline-подсказки для enum/x-enum-varnames", e)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            // Логируем ошибку в лог IDE и кратко в консоль
            LOG.warn("Не удалось вывести содержимое файла при открытии", t)
            println("[FileOpenLogger] Ошибка при чтении файла: ${file.path}: ${t.message}")
        }
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(FileOpenLogger::class.java)
    }

    /**
     * Парсит упрощённо YAML-текст и ищет пары блоков:
     *   enum:
     *     - VALUE1   <-- рядом с этими элементами нужно показать соответствующие varnames
     *   x-enum-varnames:
     *     - Value1
     * Возвращает список пар (lineIndex элемента enum, соответствующий varname).
     *
     * Ограничения:
     * - Поиск только вперёд: x-enum-varnames должен идти ПОСЛЕ enum на том же уровне отступа.
     * - Блоки определяются по отступу пробелами; табы не поддерживаются.
     * - Элементы списков определяются строками вида "- значение" с бОльшим отступом, чем у ключа.
     */
    private fun collectEnumVarnameHints(yaml: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        val lines = yaml.lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimEnd()
            val enumIndent = leadingSpaces(trimmed, line)
            if (isKeyLine(trimmed, "enum:")) {
                // Собираем элементы enum
                val enumItems = mutableListOf<Pair<Int, String>>()
                var j = i + 1
                while (j < lines.size) {
                    val l = lines[j]
                    val t = l.trimEnd()
                    val ind = leadingSpaces(t, l)
                    if (t.isBlank()) { j++; continue }
                    if (ind <= enumIndent) break
                    val item = parseDashItem(t)
                    if (item != null) {
                        enumItems.add(j to item)
                    }
                    j++
                }

                // Ищем x-enum-varnames на том же уровне отступа дальше
                varnamesSearch@ for (k in j until lines.size) {
                    val l = lines[k]
                    val t = l.trimEnd()
                    if (t.isBlank()) continue
                    val ind = leadingSpaces(t, l)
                    if (ind < enumIndent) break // вышли из текущего родителя
                    if (ind == enumIndent && isKeyLine(t, "x-enum-varnames:")) {
                        val varnames = mutableListOf<String>()
                        var m = k + 1
                        while (m < lines.size) {
                            val ll = lines[m]
                            val tt = ll.trimEnd()
                            val ii = leadingSpaces(tt, ll)
                            if (tt.isBlank()) { m++; continue }
                            if (ii <= enumIndent) break
                            val item = parseDashItem(tt)
                            if (item != null) varnames.add(item)
                            m++
                        }

                        // Сопоставляем по порядку
                        val count = minOf(enumItems.size, varnames.size)
                        for (idx in 0 until count) {
                            val (lineIdx, _) = enumItems[idx]
                            val varName = varnames[idx]
                            result.add(lineIdx to varName)
                        }
                        break@varnamesSearch
                    }
                }

                i = j
                continue
            }
            i++
        }
        return result
    }

    private fun isKeyLine(trimmed: String, key: String): Boolean {
        // Совпадение ключа в конце строки (без значения на той же строке)
        return trimmed.trimStart().endsWith(key)
    }

    private fun parseDashItem(trimmed: String): String? {
        val s = trimmed.trimStart()
        if (!s.startsWith("- ")) return null
        var value = s.substring(2).trim()
        // Убираем комментарии после значения
        val hash = value.indexOf('#')
        if (hash >= 0) value = value.substring(0, hash).trim()
        // Убираем обрамляющие кавычки
        if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\''))) {
            value = value.substring(1, value.length - 1)
        }
        return value
    }

    private fun leadingSpaces(trimmed: String, original: String): Int {
        // Количество начальных пробелов (tabs считаем как 1 символ, но не рекомендуются)
        var count = 0
        for (ch in original) {
            if (ch == ' ' || ch == '\t') count++ else break
        }
        return count
    }
}
