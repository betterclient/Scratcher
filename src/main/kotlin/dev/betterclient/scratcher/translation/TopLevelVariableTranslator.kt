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

    fun createFunction(vars: Map<TLVariable, Expression?>): Function {
        val func = Function(
            name = "initTopLevel",
            returnType = PrimitiveType.Void,
            export = false,
            warp = true,
            userAccessible = false, //idk how you would access it?? This function is created after parsing
            sourceAST = StandardLibASTGenerator.compilerLib
        )

        if (CompilationConstants.REFCOUNT_GC) {
            vars.forEach { (variable, _) ->
                func.code.code.add(
                    TLVariableAssignmentStatement(variable, variable.sourceAST, StringLiteral("null"))
                )
            }
        }

        vars.forEach { (variable, value) ->
            if(value == null) {
                func.code.code.add(
                    TLVariableAssignmentStatement(variable, variable.sourceAST, StringLiteral("null"))
                )
            } else {
                func.code.code.add(
                    TLVariableAssignmentStatement(variable, variable.sourceAST, value)
                )
            }
        }

        return func
    }
}