package dev.betterclient.scratcher.translation

import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.TemporaryScratchStmt
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator

class TopLevelVariableTranslator {
    fun translate(variable: TLVariable): ScratchVariable {
        return ScratchVariable(obfuscate("${variable.sourceAST.simplePath}::${variable.name}"))
    }

    fun createFunction(vars: Map<TLVariable, Expression?>): Function {
        val func = Function(
            name = "compiler@initTopLevel",
            returnType = PrimitiveType.Void,
            export = false,
            warp = true,
            userAccessible = false, //idk how you would access it?? This function is created after parsing
            sourceAST = StandardLibASTGenerator.compilerLib
        )

        vars.forEach { (variable, value) ->
            value?.let {
                func.code.code.add(
                    TLVariableAssignmentStatement(variable, variable.sourceAST, it)
                )
            }
        }

        return func
    }
}