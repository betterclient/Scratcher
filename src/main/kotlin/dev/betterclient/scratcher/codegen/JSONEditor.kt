package dev.betterclient.scratcher.codegen

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.toObjectArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class JSONEditor(val file: ZipFile) {
    private val overwrites = mutableMapOf<String, ByteArray>()
    val project = JSONObject(String(file.getInputStream(ZipEntry("project.json"))!!.use { it.readBytes() }))
    val workerSprite = project.getJSONArray("targets").toObjectArray().find { it.getString("name") == "Sprite1" }!!
    init {
        workerSprite.put("name", "Scratcher Worker Sprite")
        workerSprite.getJSONObject("blocks").clear()

        val extensions = project.getJSONArray("extensions")
        fun addExt(id: String) {
            var found = false
            for (i in 0 until extensions.length()) {
                if (extensions.getString(i) == id) { found = true; break }
            }
            if (!found) extensions.put(id)
        }

        addExt("pen")
        if (CompilationConstants.TURBOWARP) {
            addExt("strings")
            addExt("truefantommath")
            addExt("qxsckvarandlist")

            val extUrls = if (project.has("extensionURLs")) {
                project.getJSONObject("extensionURLs")
            } else {
                JSONObject().also { project.put("extensionURLs", it) }
            }
            extUrls.put("strings", "https://extensions.turbowarp.org/text.js")
            extUrls.put("truefantommath", "https://extensions.turbowarp.org/true-fantom/math.js")
            extUrls.put("qxsckvarandlist", "https://extensions.turbowarp.org/qxsck/var-and-list.js")
        }
    }

    fun writeTo(ifile: File) {
        //println(project.toString(4))
        overwrites["project.json"] = project.toString().toByteArray()

        ZipOutputStream(ifile.outputStream()).use { zip ->
            for (entry in file.entries().toList()) {
                if (overwrites.containsKey(entry.name)) {
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(overwrites[entry.name]!!)
                    zip.closeEntry()
                } else {
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(file.getInputStream(entry).use { it.readBytes() })
                    zip.closeEntry()
                }
            }
        }
    }
}

fun openScratchEditorFromResource(inputStream: InputStream): ScratchEditor {
    val tmpFile = File.createTempFile("tmp", ".zip")
    tmpFile.deleteOnExit()

    inputStream.use {
        tmpFile.outputStream().use {
            inputStream.copyTo(it)
        }
    }
    return ScratchEditor(JSONEditor(ZipFile(tmpFile))).apply {
        if (CompilationConstants.ADD_DUMMY_SPRITE) addDummySprite()
    }
}