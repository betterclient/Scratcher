package dev.betterclient.scratcher.codegen

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
        project.getJSONArray("extensions").put("pen")
        workerSprite.put("name", "Scratcher Worker Sprite")
        workerSprite.getJSONObject("blocks").clear()
    }

    fun writeTo(ifile: File) {
        println(project.toString(4))
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
    return ScratchEditor(JSONEditor(ZipFile(tmpFile)))
}