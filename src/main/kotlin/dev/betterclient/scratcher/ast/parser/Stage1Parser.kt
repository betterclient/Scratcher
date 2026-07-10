package dev.betterclient.scratcher.ast.parser

import com.strumenta.antlrkotlin.parsers.generated.ScratcherLangParser
import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.*
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.except.*
import dev.betterclient.scratcher.getUniqueName
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.lib.ListLib
import java.math.BigInteger

class Stage1Parser(val ctx: CompilationContext, val ast: ASTFile) {
    fun parse() {
        ast.completedStage1Parsing = true
        ast.imports.forEach { (_, ast) ->
            if (!ast.completedStage1Parsing) {
                ast.completedStage1Parsing = true
                Stage1Parser(ctx, ast).parse()
            }
        }

        if (StandardLibASTGenerator.lib.containsValue(ast)) return //do not try to parse standard lib

        parseInternal()
    }

    val localVariables = mutableListOf<LocalVariable>() //keep track of local variables
    var currentFunction: Function? = null

    private fun parseInternal() {
        for (variable in ast.variables) {
            variable.ctx?.let {
                variable.defaultValue = parseExpression(it)
            }
            variable.ctx = null
        }

        ast.eventListeners.forEach { listener ->
            listener.ctx?.let {
                listener.code.code.add(ExpressionStatement(
                    CallExpression(it, mutableListOf())
                ))
            }
        }

        currentFunction = null
        localVariables.clear()

        ast.functions.forEach {
            currentFunction = it
            localVariables.clear()
            if (ast.path != "typecheck") {
                addParameterChecks(it.code, it.parameters)
            }
            parseBlock(it.code, it.ctx!!)
            it.ctx = null
        }
        currentFunction = null
    }

    private fun addParameterChecks(
        code: CodeBlock,
        parameters: MutableList<Parameter>
    ) {
        if (CompilationConstants.DISABLE_TYPE_CHECKER || ast.simplePath == "gc_impl") return

        for (parameter in parameters) {
            if (parameter.type == Type.float) {
                val func = if (CompilationConstants.OBFUSCATION) {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkFloatObf" }
                } else {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkFloat" }
                }!!

                code.code.add(ExpressionStatement(
                    CallExpression(func, listOfNotNull(
                        ParameterExpression(parameter),
                        if (CompilationConstants.OBFUSCATION) null else
                            StringLiteral("Function: ${ast.simplePath}::${currentFunction?.name} Parameter \"${parameter.name}\" is not a float!")
                    ).toMutableList())
                ))
            } else if (parameter.type == Type.int) {
                val func = if (CompilationConstants.OBFUSCATION) {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkIntObf" }
                } else {
                    StandardLibASTGenerator.typeChecker.functions.find { it.name == "checkInt" }
                }!!

                code.code.add(ExpressionStatement(
                    CallExpression(func, listOfNotNull(
                        ParameterExpression(parameter),
                        if (CompilationConstants.OBFUSCATION) null else
                            StringLiteral("Function: ${ast.simplePath}::${currentFunction?.name} Parameter \"${parameter.name}\" is not an integer!")
                    ).toMutableList())
                ))
            }
        }
    }

    private fun parseCodeBlock(block: CodeBlock, ctx: ScratcherLangParser.CodeBlockContext, injectVariables: List<LocalVariable> = listOf()) {
        ctx.block()?.let {
            parseBlock(block, it, injectVariables)
        }
        ctx.statement()?.let {
            val prevLocalVariables = localVariables.map { vari -> vari }
            localVariables.addAll(injectVariables)

            block.code.add(parseStatement(it))

            block.localVariables.addAll(localVariables)
            localVariables.clear()
            localVariables.addAll(prevLocalVariables)
        }
    }

    private fun parseBlock(block: CodeBlock, blockCtx: ScratcherLangParser.BlockContext, injectVariables: List<LocalVariable> = listOf()) {
        val prevLocalVariables = localVariables.map { it }
        localVariables.addAll(injectVariables)
        blockCtx.statement().map { parseStatement(it); }.forEach {
            block.code.add(it)
        }
        blockCtx.returnStmt()?.let {
            block.code.add(ReturnStatement(it.expression()?.let { expr -> parseExpression(expr) }))
        }

        block.localVariables.addAll(localVariables)
        localVariables.clear()
        localVariables.addAll(prevLocalVariables)
    }

    private fun parseStatement(ctx: ScratcherLangParser.StatementContext): Statement {
        return when (val child = ctx.getChild(0)) {
            is ScratcherLangParser.VarDeclContext -> {
                val type = figureOutType(this.ctx, ast, child.type())
                val name = child.IDENTIFIER().text
                val value = parseExpression(child.expression())

                val variable = LocalVariable(name, type)
                if (variable.type == Type.void) throw VoidVariableException("Variable ${ast.simplePath}::${currentFunction?.name}::${variable.name} is type void.")
                if (localVariables.find { it.name == variable.name } != null) throw DuplicateDefinitionException("Variable ${variable.name} already exists in ${ast.simplePath}::${currentFunction?.name}")
                localVariables.add(variable)
                VariableStatement(value, variable)
            }
            is ScratcherLangParser.ExprStmtContext -> ExpressionStatement(parseExpression(child.expression()))
            is ScratcherLangParser.AssignIndexStmtContext -> {
                val list = parseExpression(child.expression(0)!!)
                val index = parseExpression(child.expression(1)!!)
                val item = parseExpression(child.expression(2)!!)

                ExpressionStatement(
                    CallExpression(
                        func = ListLib.replace,
                        listOf(list, item, index)
                    )
                )
            }
            is ScratcherLangParser.AssignStmtContext -> {
                val variableExpr = parseExpression(child.expression(0)!!)
                val assignmentExpr = parseExpression(child.expression(1)!!)
                val assignmentType = child.assignOp()
                val actualAssignment = when {
                    assignmentType.ASSIGN() != null -> {
                        assignmentExpr
                    }
                    else -> {
                        BinaryExpression(
                            left = variableExpr,
                            right = assignmentExpr,
                            operator = when {
                                assignmentType.ADD_ASSIGN() != null -> BinaryOperator.ADD
                                assignmentType.DIV_ASSIGN() != null -> BinaryOperator.DIVIDE
                                assignmentType.MUL_ASSIGN() != null -> BinaryOperator.MULTIPLY
                                assignmentType.SUB_ASSIGN() != null -> BinaryOperator.SUBTRACT
                                else -> throw GeneralCompilerException("Unknown assignment type $assignmentType")
                            }
                        )
                    }
                }

                when(variableExpr) {
                    is LocalVariableExpression -> {
                        LocalVariableAssignmentStatement(variableExpr.variable, actualAssignment)
                    }
                    is MemberExpression -> {
                        VariableAssignmentStatement(variableExpr.expression, variableExpr.member, variableExpr.struct, actualAssignment)
                    }
                    is VariableExpression -> {
                        if (!variableExpr.variable.mutable) throw GeneralCompilerException("Tried to assign to immutable field ${variableExpr.sourceAST.simplePath}::${variableExpr.variable.name}")

                        TLVariableAssignmentStatement(variableExpr.variable, variableExpr.sourceAST, actualAssignment)
                    }
                    else -> throw GeneralCompilerException("Not mutable, tried to assign to non assignable expression $variableExpr")
                }
            }
            is ScratcherLangParser.IfStmtContext -> {
                parseIfStatement(child)
            }
            is ScratcherLangParser.WhileStmtContext -> {
                val cond = parseExpression(child.expression())
                val whileBlock = CodeBlock().also { parseBlock(it, child.block()) }
                WhileStatement(cond, whileBlock)
            }
            is ScratcherLangParser.RepeatStmtContext -> {
                val amount = parseExpression(child.expression())
                val repeatBlock = CodeBlock().also { parseBlock(it, child.block()) }
                RepeatStatement(amount, repeatBlock)
            }
            is ScratcherLangParser.ReturnIfStmtContext -> {
                val returnExpr = if (child.expression().size == 2) parseExpression(child.expression(0)!!) else null
                val cond = parseExpression(child.expression().last())

                IfStatement(cond, CodeBlock().also {
                    it.code.add(ReturnStatement(returnExpr))
                })
            }
            is ScratcherLangParser.ForStmtContext -> {
                parseForStatement(child)
            }
            else -> throw NotImplementedException("Unknown statement type: ${child?.text}")
        }
    }

    private fun parseIfStatement(child: ScratcherLangParser.IfStmtContext): Statement {
        val cond = parseExpression(child.expression())
        val thenBlock = CodeBlock().also { parseBlock(it, child.block(0)!!) }

        return if (child.ELSE() != null) {
            val elseBlock = if (child.ifStmt() != null) {
                //else if!!!
                val nestedIf = parseIfStatement(child.ifStmt()!!)

                CodeBlock().also {
                    it.code.add(nestedIf)
                }
            } else {
                CodeBlock().also { parseBlock(it, child.block(1)!!) }
            }
            IfElseStatement(cond, thenBlock, elseBlock)
        } else {
            IfStatement(cond, thenBlock)
        }
    }

    private fun parseExpression(ctx: ScratcherLangParser.ExpressionContext): Expression {
        return when (ctx) {
            is ScratcherLangParser.ParensExprContext -> parseExpression(ctx.expression())
            is ScratcherLangParser.CallExprContext -> figureOutFunction(ctx.functionIdentifier(), ctx.argList())
            is ScratcherLangParser.UnaryExprContext -> UnaryExpression(
                operator = when {
                    ctx.PLUS() != null -> UnaryOperator.PLUS
                    ctx.MINUS() != null -> UnaryOperator.MINUS
                    ctx.BANG() != null -> UnaryOperator.NOT
                    else -> throw NotImplementedException("Unknown or missing unary operator in expression: ${ctx.text}")
                },
                expression = parseExpression(ctx.expression())
            )
            is ScratcherLangParser.LiteralExprContext -> parseLiteral(ctx.literal())
            is ScratcherLangParser.IdExprContext -> parseIdentifier(ctx.text)
            is ScratcherLangParser.MultExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.MOD() != null -> BinaryOperator.MODULO
                    ctx.SLASH() != null -> BinaryOperator.DIVIDE
                    ctx.STAR() != null -> BinaryOperator.MULTIPLY
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!),
            )
            is ScratcherLangParser.AddExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.PLUS() != null -> BinaryOperator.ADD
                    ctx.MINUS() != null -> BinaryOperator.SUBTRACT
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.RelExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.GE() != null -> BinaryOperator.GREATER_EQUAL
                    ctx.GT() != null -> BinaryOperator.GREATER_THAN
                    ctx.LE() != null -> BinaryOperator.LESS_EQUAL
                    ctx.LT() != null -> BinaryOperator.LESS_THAN
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.EqExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = when {
                    ctx.EQ() != null -> BinaryOperator.EQUAL
                    ctx.NE() != null -> BinaryOperator.NOT_EQUAL
                    else -> throw NotImplementedException("Unknown binary operator in expression: ${ctx.text}")
                },
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.AndExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = BinaryOperator.AND,
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.OrExprContext -> BinaryExpression(
                left = parseExpression(ctx.expression(0)!!),
                operator = BinaryOperator.OR,
                right = parseExpression(ctx.expression(1)!!)
            )
            is ScratcherLangParser.ScopeExprContext -> parseScopeExpr(ctx)
            is ScratcherLangParser.MemberExprContext -> parseMemberExpr(ctx)
            is ScratcherLangParser.NullExprContext -> NullExpression
            is ScratcherLangParser.AssertNonNullContext -> {
                NonNullAssertExpression(parseExpression(ctx.expression()))
            }
            is ScratcherLangParser.ListCreationExprContext -> {
                val list = figureOutType(this.ctx, ast, ctx.type())
                CallExpression(
                    ListLib.newList,
                    mutableListOf(
                        StringLiteral( //so sorry for this but im not bothering adding more expressions just for this one fricking function
                            list.toString()
                        ),
                        StringLiteral(
                            "l"
                        )
                    )
                )
            }
            is ScratcherLangParser.IndexExprContext -> {
                val list = parseExpression(ctx.expression(0)!!)
                val index = parseExpression(ctx.expression(1)!!)
                CallExpression(
                    func = ListLib.itemAt,
                    listOf(list, index)
                )
            }
            is ScratcherLangParser.WhenExprContext -> {
                parseWhenExpr(ctx.whenExpression())
            }
            is ScratcherLangParser.IfExprContext -> {
                WhenExpression(
                    null,
                    parseIfExpr(ctx.ifExpression())
                )
            }
            else -> throw NotImplementedException("No parser for expr ${ctx.text} yet!")
        }
    }

    fun parseIfExpr(ctx: ScratcherLangParser.IfExpressionContext): List<WhenBranch> {
        val branch = mutableListOf<WhenBranch>()

        val thenCond = parseExpression(ctx.expression())
        val thenBlock = parseExprBlock(ctx.exprBlock(0)!!)
        branch.add(WhenBranch(
            thenCond, thenBlock
        ))
        if (ctx.ifExpression() == null) {
            val elseBlock = parseExprBlock(ctx.exprBlock(1)!!)
            branch.add(WhenBranch(
                BooleanLiteral(true), elseBlock, isElse = true
            ))
        } else {
            branch.addAll(parseIfExpr(ctx.ifExpression()!!))
            if (branch.filter { it.cond is BooleanLiteral && it.cond.value }.size > 1) {
                throw DuplicateDefinitionException("Duplicate else blocks in if expression $ctx")
            }
        }

        return branch
    }

    private fun parseExprBlock(block: ScratcherLangParser.ExprBlockContext): CodeBlock {
        val out = CodeBlock()

        if (block.LBRACE() == null) {
            out.code.add(ExpressionStatement(parseExpression(block.expression())))
            return out
        }

        val prevLocalVariables = localVariables.toList()
        block.statement().map(::parseStatement).forEach(out.code::add)
        val result = parseExpression(block.expression())
        out.code.add(ExpressionStatement(result))

        out.localVariables.addAll(localVariables)
        localVariables.clear()
        localVariables.addAll(prevLocalVariables)

        return out
    }

    private fun parseWhenExpr(ctx: ScratcherLangParser.WhenExpressionContext): Expression {
        val subject = ctx.expression()?.let { parseExpression(it) }
        val subjectVar = subject?.let { LocalVariable("whenStatement@subject${getUniqueName()}", ExpressionTypes.getExpressionType(this.ctx, it)) }
        val subjectAssignment = subjectVar?.let { VariableStatement(subject, it) }
        val entries = ctx.whenEntry()
        if (entries.isEmpty()) {
            throw GeneralCompilerException("When expression must have at least one branch at ${ctx.position?.start}")
        }
        if (entries.count { it.whenCondition().ELSE() != null } > 1) {
            throw DuplicateDefinitionException("Duplicate ELSE condition in ${ctx.position?.start}")
        }
        for (i in entries.indices) {
            if (entries[i].whenCondition().ELSE() != null && i != entries.lastIndex) {
                throw GeneralCompilerException("ELSE must be the last branch in when expression at ${ctx.position?.start}")
            }
        }

        return WhenExpression(
            subjectAssignment,
            entries.map { entry ->
                val isElse = entry.whenCondition().ELSE() != null
                val cond = entry.whenCondition().expression()?.let {
                    val expr = parseExpression(it)
                    if (subjectVar != null) {
                        BinaryExpression(
                            left = LocalVariableExpression(subjectVar),
                            right = expr,
                            operator = BinaryOperator.EQUAL
                        )
                    } else expr
                } ?: BooleanLiteral(true) //else

                val branchBlock = CodeBlock()
                if (entry.expression() != null) {
                    branchBlock.code.add(ExpressionStatement(parseExpression(entry.expression()!!)))
                } else if (entry.codeBlock() != null) {
                    val block = entry.codeBlock()!!
                    block.block()?.let {
                        parseBlock(branchBlock, it)
                    }
                    block.statement()?.let {
                        branchBlock.code.add(parseStatement(it))
                    }

                }

                WhenBranch(
                    cond,
                    branchBlock,
                    isElse
                )
            }
        )
    }

    private fun parseMemberExpr(ctx: ScratcherLangParser.MemberExprContext): Expression {
        val enum = when (val leftExpr = ctx.expression()) {
            is ScratcherLangParser.IdExprContext -> {
                val name = leftExpr.text
                ast.enums.find { it.name == name }
                    ?: ast.imports.values.flatMap { it.enums }.find { it.name == name }
            }
            is ScratcherLangParser.ScopeExprContext -> {
                val importName = leftExpr.IDENTIFIER(0)!!.text
                val enumName = leftExpr.IDENTIFIER(1)!!.text
                ast.imports[importName]?.enums?.find { it.name == enumName }
            }
            else -> null
        }

        if (enum != null) {
            val memberName = ctx.IDENTIFIER().text
            val ordinal = enum.values.indexOf(memberName)
            if (ordinal == -1) {
                throw NotFoundException("Enum ${enum.name} does not have value $memberName")
            }
            return EnumLiteral(enum, memberName, ordinal)
        }

        val structExpr = parseExpression(ctx.expression())
        val struct = ExpressionTypes.getExpressionType(this.ctx, structExpr).let { type ->
            val baseType = type.asNonNull()
            baseType.sourceAST!!.structs.find { it.type == baseType }?: throw GeneralCompilerException("$type is a primitive type(?) at ${ctx.position}, expected a struct, found $baseType")
        }
        val memberName = ctx.IDENTIFIER().text

        val member = struct.parameters.find { it.name == memberName }?: throw NotFoundException("Struct ${struct.name} does not have $memberName")

        return MemberExpression(structExpr, member, struct)
    }

    private fun parseScopeExpr(ctx: ScratcherLangParser.ScopeExprContext): Expression {
        //this is just for accessing tl variables from imports
        val import = ctx.IDENTIFIER(0)!!.text
        val variable = ctx.IDENTIFIER(1)!!.text

        return ast.imports[import]?.variables?.find { it.name == variable }?.let { VariableExpression(it, ast.imports[import]!!) }
            ?: throw NotFoundException("${ctx.text} not found")
    }

    private fun parseIdentifier(text: String): Expression {
        //either top level variable or local variable or parameter
        val variableFinding = localVariables.find { it.name == text }
        if (variableFinding != null) {
            return LocalVariableExpression(variableFinding)
        }

        val parameterFinding = currentFunction?.parameters?.find { it.name == text }
        if (parameterFinding != null) {
            return ParameterExpression(parameterFinding)
        }

        val variable = ast.variables.find { it.name == text }?: throw NotFoundException("Variable $text not found")
        return VariableExpression(variable, ast)
    }

    private fun figureOutFunction(
        funcCall: ScratcherLangParser.FunctionIdentifierContext,
        argList: ScratcherLangParser.ArgListContext?
    ): Expression {
        val sourceAST = if (funcCall.IDENTIFIER() != null) {
            ast
        } else {
            val import = funcCall.typePath()!!.IDENTIFIER(0)!!.text
            ast.imports[import]?: throw NotFoundException("Import not found $import for ${funcCall.text}.")
        }

        val funcName = if (funcCall.IDENTIFIER() != null) {
            funcCall.IDENTIFIER()!!.text
        } else {
            funcCall.typePath()!!.IDENTIFIER(1)!!.text
        }

        val expectedArgListTypes = argList?.expression()?.map { expr -> ExpressionTypes.getExpressionType(this.ctx, parseExpression(expr)) }?: listOf()
        val args = argList?.expression()?.map { parseExpression(it) }?: listOf()

        var resolvedFunc = sourceAST.functions.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            matchesArgumentsExactly(expectedArgListTypes, foundArgListTypes)
        }

        if (sourceAST == StandardLibASTGenerator.listLib && funcName != "newList") {
            //AAAAAAAAAAAAAAAAAAAAAAAAAAA
            resolvedFunc = sourceAST.functions.find { it.name == funcName }?: throw NotFoundException("Function $funcName not found. in ${ast.simplePath}::${currentFunction?.name} at ${funcCall.text}")
        }

        if (resolvedFunc == null) {
            resolvedFunc = sourceAST.functions.find {
                if (it.name != funcName) return@find false

                val foundArgListTypes = it.parameters.map { par -> par.type }
                matchesArguments(expectedArgListTypes, foundArgListTypes)
            }
        }

        resolvedFunc?.let {
            if (!it.userAccessible) {
                throw NotFoundException("Function ${funcCall.text} is not accessible.")
            }
            return CallExpression(
                func = it,
                arguments = args
            )
        }

        sourceAST.structs.find {
            if (it.name != funcName) return@find false

            val foundArgListTypes = it.parameters.map { par -> par.type }
            return@find matchesArguments(
                expectedArgListTypes,
                foundArgListTypes
            )
        }?.let {
            return CallExpression(it.allocFunc, args)
        }

        val targetFunc = "$funcName(${expectedArgListTypes.joinToString(", ") { it.name }})"
        val candidates = mutableListOf<String>()
        sourceAST.functions.filter { it.name == funcName }.forEach { func ->
            candidates.add("Function \"${func.returnType.name} ${func.name}(${func.parameters.joinToString(", ") { "${it.type} ${it.name}" }})\"")
        }
        sourceAST.structs.filter { it.name == targetFunc }.forEach { struct ->
            candidates.add("Struct \"${struct.name}\"")
        }

        throw NotFoundException("Function $targetFunc not found, candidates: \n${candidates.joinToString("\n")}\nStackTrace:")
    }

    private fun matchesArgumentsExactly(
        provided: List<Type>,
        expected: List<Type>
    ): Boolean {
        if (provided.size != expected.size) return false
        return provided.zip(expected).all { (from, to) ->
            from == to
        }
    }

    private fun matchesArguments(
        provided: List<Type>,
        expected: List<Type>
    ): Boolean {
        if (provided.size != expected.size) return false

        return provided.zip(expected).all { (from, to) ->
            from.isAssignable(to)
        }
    }

    private fun parseLiteral(ctx: ScratcherLangParser.LiteralContext): Expression {
        return when {
            ctx.FALSE() != null -> BooleanLiteral(false)
            ctx.TRUE() != null -> BooleanLiteral(true)
            ctx.FLOAT() != null -> FloatLiteral(ctx.FLOAT()!!.text.toBigDecimalOrNull()?: throw TypeException(Type.float, Type.nullType, "${ctx.FLOAT()?.text} is not a float!"))
            ctx.INT() != null -> IntLiteral(ctx.INT()!!.text.toBigIntegerOrNull()?: throw TypeException(Type.int, Type.nullType, "${ctx.INT()?.text} is not an int!"))
            ctx.stringLiteral() != null -> parseStringInterp(ctx.stringLiteral()!!.stringPart())
            else -> throw NotImplementedException("$ctx is not one of the expected types.")
        }
    }

    private fun parseStringInterp(parts: List<ScratcherLangParser.StringPartContext>): Expression {
        if (parts.isEmpty()) return StringLiteral("")
        val exprs = parts.map { part ->
            when {
                part.STR_TEXT() != null -> {
                    StringLiteral(part.STR_TEXT()!!.text)
                }
                part.STR_ESC() != null -> {
                    StringLiteral(unescapeString(part.STR_ESC()!!.text))
                }
                part.DOLLAR() != null -> {
                    StringLiteral("$")
                }
                part.interpolation() != null -> {
                    parseExpression(part.interpolation()!!.expression())
                }
                else -> StringLiteral("")
            }
        }
        val exprsReduced = if (exprs.size == 1) {
            if (ExpressionTypes.getExpressionType(this.ctx, exprs[0]) == Type.str) {
                exprs[0]
            } else {
                ConcatExpression(exprs[0], StringLiteral(""))
            }
        } else {
            exprs.reduce { left, right ->
                ConcatExpression(left, right)
            }
        }

        return exprsReduced
    }

    private fun unescapeString(esc: String): String {
        return when (esc) {
            "\\n" -> "\n"
            "\\t" -> "\t"
            "\\r" -> "\r"
            "\\\"" -> "\""
            "\\\\" -> "\\"
            "\\$" -> "$"
            else -> if (esc.startsWith("\\")) esc.substring(1) else esc
        }
    }

    private fun parseForStatement(child: ScratcherLangParser.ForStmtContext): IfStatement {
        val list = parseExpression(child.expression())
        val listVar = LocalVariable(
            obfuscate("compiler@forStmtList${getUniqueName()}"),
            ExpressionTypes.getExpressionType(this.ctx, list)
        )
        val variable = LocalVariable(
            name = child.IDENTIFIER().text,
            type = figureOutType(this.ctx, ast, child.type())
        )
        val indexVariable = LocalVariable(
            obfuscate("compiler@forStmtIndex${getUniqueName()}"),
            Type.int
        )

        //is there a way to unsee code that you wrote?
        //this is a hack for both: hiding these variables from user code and bypassing the single statement limit
        return IfStatement(
            condition = BooleanLiteral(true),
            thenBlock = CodeBlock().also {
                val prevLocalVariables = localVariables.map { variable -> variable }
                localVariables.add(listVar)
                localVariables.add(indexVariable)
                it.code.add(VariableStatement(list, listVar))
                it.code.add(VariableStatement(IntLiteral(BigInteger.ZERO), indexVariable))
                it.code.add(VariableStatement(null, variable))
                it.code.add(
                    RepeatStatement(
                        amount = CallExpression(
                            func = ListLib.length,
                            arguments = listOf(LocalVariableExpression(listVar))
                        ),
                        block = CodeBlock().also { inner ->
                            parseBlock(inner, child.block(), injectVariables = listOf(variable))
                            inner.code.add(
                                0, LocalVariableAssignmentStatement(
                                    variable, CallExpression(
                                        func = ListLib.itemAt,
                                        listOf(LocalVariableExpression(listVar), LocalVariableExpression(indexVariable))
                                    )
                                )
                            )
                            inner.code.add(
                                LocalVariableAssignmentStatement(
                                    indexVariable, BinaryExpression(
                                        left = LocalVariableExpression(indexVariable),
                                        right = IntLiteral(1.toBigInteger()),
                                        operator = BinaryOperator.ADD
                                    )
                                )
                            )
                        }
                    ))
                it.localVariables.addAll(localVariables)
                localVariables.clear()
                localVariables.addAll(prevLocalVariables)
            }
        )
    }
}