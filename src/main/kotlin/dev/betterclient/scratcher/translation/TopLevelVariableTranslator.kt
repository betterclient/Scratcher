package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class TopLevelVariableTranslator {
    fun translate(variable: TLVariable): ScratchVariable {
        if (variable.name.startsWith("Exported Return: ") && variable.sourceAST == StandardLibASTGenerator.compilerLib) {
            return ScratchVariable(variable.name)
        }
        return ScratchVariable(obfuscate("${variable.sourceAST.simplePath}::${variable.name}"))
    }

    fun createFunction(vars: Map<TLVariable, Expression?>, reachableLists: List<TLStaticList>): Function {
        val func = Function(
            name = "initTopLevel",
            returnType = PrimitiveType.Void,
            export = false,
            warp = true,
            operator = false,
            private = false,
            userAccessible = false, //idk how you would access it?? This function is created after parsing
            sourceAST = StandardLibASTGenerator.compilerLib
        )

        if (CompilationConstants.REFCOUNT_GC) {
            vars.keys.forEach { func.code.code.add(assign(it, null)) }
        }
        vars.forEach { (variable, value) -> func.code.code.add(assign(variable, value)) }

        reachableLists.filter { it.scratchList.items.isEmpty() }
            .forEach { func.code.code.add(StaticListClearStatement(it)) }

        return func
    }

    private fun assign(variable: TLVariable, value: Expression?) =
        TLVariableAssignmentStatement(variable, variable.sourceAST, value ?: StringLiteral("null"))
}