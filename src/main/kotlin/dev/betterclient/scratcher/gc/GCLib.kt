package dev.betterclient.scratcher.gc

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTEventListener
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.BooleanLiteral
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.ExpressionLowerResult
import dev.betterclient.scratcher.ast.ExpressionStatement
import dev.betterclient.scratcher.ast.InlineStandardLibFunction
import dev.betterclient.scratcher.ast.ListType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.TLVariable
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.VariableExpression
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.CallFunction
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.codegen.opcode.ScratchList
import dev.betterclient.scratcher.codegen.opcode.ScratchVariable
import dev.betterclient.scratcher.obfuscate
import dev.betterclient.scratcher.std.StandardLibASTGenerator
import dev.betterclient.scratcher.std.dsl.*
import dev.betterclient.scratcher.std.lib.ListLib
import dev.betterclient.scratcher.std.lib.MemoryLib

//this just contains helper functions, actual gc implemented in resources/gc.sc
object GCLib {
    val markedList = ScratchList(obfuscate("GC: Marked"))
    val gcList = ScratchList(obfuscate("Type metadata"))
    val fieldsList = ScratchList(obfuscate("Type fields"))
    val rootsList = ScratchList(obfuscate("GC: Roots"))
    val reflectList = ScratchList(obfuscate("GC: TLReflect"))

    fun init(lib: ASTFile) {
        accessFunctions(lib, MemoryLib.freeList, "FreeList")
        accessFunctions(lib, rootsList, "Roots")
        accessFunctions(lib, reflectList, "Reflect")

        markedListFunctions(lib)

        compileInline(
            lib,
            "isStack",
            parameters = mutableListOf(Parameter("type", PrimitiveType.Str)),
            returnType = PrimitiveType.Bool
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                (((originalIndex - 1.sc) * 3.sc) + 1.sc).lower()
            )
        }

        compileInline(
            lib,
            "getFieldsStart",
            parameters = mutableListOf(Parameter("type", PrimitiveType.Str)),
            returnType = PrimitiveType.Integer
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                (((originalIndex - 1.sc) * 3.sc) + 2.sc).lower()
            )
        }

        compileInline(
            lib,
            "getFieldsCount",
            parameters = mutableListOf(Parameter("type", PrimitiveType.Str)),
            returnType = PrimitiveType.Integer
        ) {
            val originalIndex = DSLFromCreator { it[0] }
            ListExpressions.ItemAtIndex(
                gcList,
                (((originalIndex - 1.sc) * 3.sc) + 3.sc).lower()
            )
        }

        compileInline(
            lib,
            "getFieldType",
            parameters = mutableListOf(Parameter("index", PrimitiveType.Integer)),
            returnType = PrimitiveType.Str
        ) {
            ListExpressions.ItemAtIndex(fieldsList, it[0])
        }

        compileInline(
            lib,
            "getHeap",
            parameters = mutableListOf(Parameter("index", PrimitiveType.Integer)),
            returnType = PrimitiveType.Integer
        ) {
            ListExpressions.ItemAtIndex(MemoryLib.heap, it[0])
        }

        compileInline(
            lib,
            "getHeapSize",
            returnType = PrimitiveType.Integer
        ) {
            ListExpressions.LengthOfList(MemoryLib.heap)
        }

        compileInline(
            lib,
            "getListCapacity",
            parameters = mutableListOf(Parameter("addr", PrimitiveType.Integer)),
            returnType = PrimitiveType.Integer
        ) {
            ListExpressions.ItemAtIndex(
                MemoryLib.heap,
                if (ListLib.capacityOffset == 0) it[0] else OperatorExpressions.BinaryExpression(
                    left = it[0],
                    right = ListLib.capacityOffset.toString().scratch,
                    operator = OperatorExpressions.BinaryOperator.ADD
                )
            )
        }

        compileInline(
            lib,
            "getListDataPtr",
            parameters = mutableListOf(Parameter("addr", PrimitiveType.Integer)),
            returnType = PrimitiveType.Integer
        ) {
            ListExpressions.ItemAtIndex(
                MemoryLib.heap,
                if (ListLib.dataPtrOffset == 0) it[0] else OperatorExpressions.BinaryExpression(
                    left = it[0],
                    right = ListLib.dataPtrOffset.toString().scratch,
                    operator = OperatorExpressions.BinaryOperator.ADD
                )
            )
        }

        compileInline(
            lib,
            "getListHeaderSize",
            returnType = PrimitiveType.Integer
        ) {
            ListLib.headerSize.toString().scratch
        }

        compileInline(
            lib,
            "freeHeapBlock",
            parameters = mutableListOf(Parameter("index", PrimitiveType.Integer), Parameter("size", PrimitiveType.Integer))
        ) {
            CallFunction(
                func = MemoryLib.free.precompiledCode,
                args = listOf(it[0], it[1])
            )
        }

        freeFunc(lib, ListType(PrimitiveType.Str), "StrArray")
        freeFunc(lib, ListType(PrimitiveType.Integer), "IntArray")

        lib.functions.add(InlineStandardLibFunction(
            "isReflectGC",
            returnType = PrimitiveType.Bool,
            sourceAST = lib,
            useLocal = false,
            warp = true,
            realCode = {
                ExpressionLowerResult(BooleanLiteral(CompilationConstants.REFLECT_GC))
            }
        ))

        compileInline(
            lib,
            "reflect",
            returnType = PrimitiveType.Integer,
            parameters = mutableListOf(Parameter("varName", PrimitiveType.Str))
        ) {
            ListExpressions.ReflectVariable(it[0])
        }
    }

    private fun freeFunc(
        lib: ASTFile,
        type: Type,
        name: String
    ) {
        compileInline(
            lib,
            "free$name",
            parameters = mutableListOf(Parameter("array", type)),
        ) {
            CallFunction(
                func = ListLib.free.precompiledCode,
                args = listOf(it[0])
            )
        }
    }

    private fun markedListFunctions(lib: ASTFile) {
        accessFunctions(lib, markedList, "Marked")
        compileInline(
            lib,
            "addMarked",
            parameters = mutableListOf(Parameter("index", PrimitiveType.Integer))
        ) {
            ListStatements.AddToList(
                markedList,
                it[0]
            )
        }
        compileInline(
            lib,
            "isMarked",
            parameters = mutableListOf(Parameter("item", PrimitiveType.Integer)),
            returnType = PrimitiveType.Bool
        ) {
            ListExpressions.ContainsItemInList(
                markedList,
                it[0]
            )
        }
        compileInline(
            lib,
            "clearMarked"
        ) {
            ListStatements.ClearList(markedList)
        }
    }

    private fun accessFunctions(lib: ASTFile, list: ScratchList, name: String) {
        compileInline(
            lib,
            "get$name",
            parameters = mutableListOf(Parameter("index", PrimitiveType.Integer)),
            returnType = PrimitiveType.Str
        ) {
            ListExpressions.ItemAtIndex(
                list,
                it[0]
            )
        }

        compileInline(
            lib,
            "lengthOf$name",
            returnType = PrimitiveType.Integer
        ) {
            ListExpressions.LengthOfList(list)
        }
    }

    fun gcFuncs(): List<ASTEventListener> {
        if (!CompilationConstants.AUTOMATIC_GC) return listOf()

        return if (CompilationConstants.MARK_AND_SWEEP_GC) {
            StandardLibASTGenerator.gc.eventListeners
        } else listOf()
    }

    fun populateList(editor: ScratchEditor) {
        val fields = mutableListOf<String>()
        var currentFieldIndex = 1

        gcList.items.addAll(
            gcNames.flatMap { info ->
                val typeFields = info.toGCList().split("-").filter { it.isNotEmpty() }
                val start = currentFieldIndex
                val count = typeFields.size
                fields.addAll(typeFields)
                currentFieldIndex += count

                listOf(
                    (info is StackGCInfo).toString(),
                    start.toString(),
                    count.toString()
                )
            }
        )

        fieldsList.items.addAll(fields)

        editor.addList(gcList)
        editor.addList(fieldsList)
        editor.addList(markedList)
        editor.addList(rootsList)
        editor.addList(reflectList)
    }

    fun initCaller(gc: ASTFile, gcLib: ASTFile) {
        gcLib.functions.add(gc.functions.find { it.name == "collect" }!!)
    }

    fun generate(vars: List<TLVariable>, translate: (TLVariable) -> ScratchVariable) {
        val eligible = vars.filter { !it.type.isPrimitive }
        val markListFunc = StandardLibASTGenerator.gc.functions.find { it.name == "markList" }!!
        val markStructFunc = StandardLibASTGenerator.gc.functions.find { it.name == "markStruct" }!!
        val original = StandardLibASTGenerator.gc.functions.find { it.name == "markTopLevels" }!!
        if (CompilationConstants.REFLECT_GC) {
            reflectList.items.addAll(eligible.flatMap { variable ->
                listOf(
                    if (variable.type is ListType) {
                        "${"l".repeat(variable.type.toString().count { it == '[' })}${findGC((variable.type as ListType).raw())}"
                    } else {
                        findGC(variable.type).toString()
                    },
                    translate(variable).name
                )
            })
            return
        }

        original.code.code.clear()
        original.code.code.addAll(
            eligible.map { variable ->
                val typeStr = if (variable.type is ListType) {
                    "${"l".repeat(variable.type.toString().count { it == '[' })}${findGC((variable.type as ListType).raw())}"
                } else {
                    findGC(variable.type).toString()
                }

                ExpressionStatement(
                    CallExpression(
                        func = if (typeStr.contains("l")) {
                            markListFunc
                        } else {
                            markStructFunc
                        },
                        arguments = listOf(
                            VariableExpression(variable, variable.sourceAST),
                            StringLiteral(typeStr)
                        )
                    )
                )
            }
        )
    }
}