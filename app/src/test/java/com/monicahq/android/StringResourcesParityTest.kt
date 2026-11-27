package com.monicahq.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language must have every string, with the same placeholders, so that a translation is
 * never forgotten in a pull request (constitution, principle VI).
 */
class StringResourcesParityTest {
    // Unit tests run with the module directory as working directory.
    private val resDir = File("src/main/res")

    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z%]""")

    private fun strings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private fun placeholders(text: String): List<String> =
        placeholder
            .findAll(text)
            .map { it.value }
            .sorted()
            .toList()

    private val default by lazy { strings(File(resDir, "values/strings.xml")) }

    private val translations by lazy {
        resDir
            .listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.exists() }
            .associate { it.parentFile.name to strings(it) }
    }

    @Test
    fun `French is provided`() {
        assertTrue(translations.containsKey("values-fr"))
    }

    @Test
    fun `every language has exactly the English keys`() {
        translations.forEach { (folder, values) ->
            assertEquals("Keys of $folder", default.keys.sorted(), values.keys.sorted())
        }
    }

    @Test
    fun `every translation keeps the English placeholders`() {
        translations.forEach { (folder, values) ->
            default.forEach { (key, english) ->
                values[key]?.let {
                    assertEquals("Placeholders of $key in $folder", placeholders(english), placeholders(it))
                }
            }
        }
    }
}
