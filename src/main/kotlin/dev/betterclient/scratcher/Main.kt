package dev.betterclient.scratcher

import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CodeBlock
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.IfElseStatement
import dev.betterclient.scratcher.ast.IfStatement
import dev.betterclient.scratcher.ast.LocalVariableAssignmentStatement
import dev.betterclient.scratcher.ast.RepeatStatement
import dev.betterclient.scratcher.ast.ReturnStatement
import dev.betterclient.scratcher.ast.TLVariableAssignmentStatement
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableAssignmentStatement
import dev.betterclient.scratcher.ast.VariableStatement
import dev.betterclient.scratcher.ast.WhileStatement
import dev.betterclient.scratcher.ast.parser.ASTReader
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.ast.parser.Stage1Parser
import dev.betterclient.scratcher.ast.parser.TypeAnalysis
import dev.betterclient.scratcher.codegen.ast.BoolOperatorExpressions
import dev.betterclient.scratcher.codegen.ast.CallFunction
import dev.betterclient.scratcher.codegen.ast.ControlStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.SBinaryOperator
import dev.betterclient.scratcher.codegen.ast.SBoolParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchASTEventListener
import dev.betterclient.scratcher.codegen.ast.ScratchASTFunction
import dev.betterclient.scratcher.codegen.ast.ScratchBoolExpression
import dev.betterclient.scratcher.codegen.ast.ScratchFuncArgument
import dev.betterclient.scratcher.codegen.ast.ScratchLiteralStringExpression
import dev.betterclient.scratcher.codegen.ast.ScratchStringParameterExpression
import dev.betterclient.scratcher.codegen.ast.ScratchType
import dev.betterclient.scratcher.codegen.wrapper.ScratchFunction
import dev.betterclient.scratcher.codegen.wrapper.ScratchRealString
import dev.betterclient.scratcher.codegen.wrapper.autoSetNext
import dev.betterclient.scratcher.codegen.opcode.*
import dev.betterclient.scratcher.codegen.openScratchEditorFromResource
import dev.betterclient.scratcher.translation.FunctionExpressionLowering
import dev.betterclient.scratcher.translation.FunctionReachability
import dev.betterclient.scratcher.translation.FunctionStructureTranslator
import dev.betterclient.scratcher.translation.ReParseLocalVariables
import dev.betterclient.scratcher.translation.ScratchFunctionTranslator
import java.io.File

fun main() {
    val editor = openScratchEditorFromResource(
        ::main.javaClass.getResourceAsStream("/proj.sb3")!!
    )

    val ast = compile(File("helloworld.sc"))
    println("Reachability")
    val reachableFunctions = FunctionReachability(ast).reachableFunctions
    println("Lower expressions")
    reachableFunctions.forEach { FunctionExpressionLowering(it).run() }
    reachableFunctions.forEach { it.returnType = Type.void }
    reachableFunctions.forEach { ReParseLocalVariables(it).run() }

    val translator = FunctionStructureTranslator()
    //store it as a pair cause we need the original func for the code itself
    val scratchStubs = reachableFunctions.map { translator.translate(it) to it }
    println("Translate code")
    scratchStubs.forEach { (scratchAst, normalAst) -> ScratchFunctionTranslator(normalAst, scratchAst).run() }

    println("Compile to scratch")
    scratchStubs.map { it.first }.forEach { editor.addFunction(it) }
    editor.writeTo(File("out.sb3"))
}

fun compile(sourceFile: File): ASTFile {
    val context = CompilationContext()
    println("Initial parse")
    val ast = ASTReader(context, sourceFile.readText(), sourceFile.absolutePath).read()
    println("Code parse")
    Stage1Parser(context, ast).parse()
    println("Static Type Checking")
    TypeAnalysis(context, ast).run()

    return ast
}
