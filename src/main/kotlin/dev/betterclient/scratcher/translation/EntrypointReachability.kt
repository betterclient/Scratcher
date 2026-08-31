package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile

class EntrypointReachability {
    val searched = mutableListOf<ASTFile>()
    fun run(source: ASTFile): List<ASTEventListener> {
        val result = mutableListOf<ASTEventListener>()
        if (searched.contains(source)) return result
        searched.add(source)

        result.addAll(source.eventListeners)
        result.addAll(source.imports.flatMap { (_, file) -> run(file) })

        result.addAll((source.flatImportNames.values + source.wildcardImportSources).flatMap { run(it) })
        return result
    }
}