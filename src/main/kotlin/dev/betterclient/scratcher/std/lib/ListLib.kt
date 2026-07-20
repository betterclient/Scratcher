package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.Function
import dev.betterclient.scratcher.ast.ListType
import dev.betterclient.scratcher.ast.Parameter
import dev.betterclient.scratcher.ast.PrimitiveType
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.codegen.ast.ListExpressions
import dev.betterclient.scratcher.codegen.ast.ListStatements
import dev.betterclient.scratcher.codegen.ast.OperatorExpressions
import dev.betterclient.scratcher.codegen.ast.scratch
import dev.betterclient.scratcher.ast.GeneralCompilerException
import dev.betterclient.scratcher.ast.NotFoundException
import dev.betterclient.scratcher.ast.TypeException
import dev.betterclient.scratcher.std.dsl.CodeBuilder
import dev.betterclient.scratcher.std.dsl.DSLExpression
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.compileInline
import dev.betterclient.scratcher.std.dsl.concat
import dev.betterclient.scratcher.std.dsl.equals
import dev.betterclient.scratcher.std.dsl.gt
import dev.betterclient.scratcher.std.dsl.lt
import dev.betterclient.scratcher.std.dsl.minus
import dev.betterclient.scratcher.std.dsl.plus
import dev.betterclient.scratcher.std.dsl.sc
import dev.betterclient.scratcher.std.dsl.times

object ListLib {
    lateinit var newList: StandardLibASTFunction
    lateinit var add: StandardLibASTFunction
    lateinit var remove: StandardLibASTFunction
    lateinit var itemAt: Function
    lateinit var clear: Function
    lateinit var length: Function
    lateinit var replace: Function
    lateinit var contains: StandardLibASTFunction
    lateinit var reserve: StandardLibASTFunction
    lateinit var free: StandardLibASTFunction

    val listFuncs by lazy {
        listOf(newList, add, remove, itemAt, clear, length, replace, contains, reserve, free)
    }

    fun init(lib: ASTFile, editor: ScratchEditor) {
        generateFunctions(lib, editor)
    }

    private fun generateFunctions(
        lib: ASTFile,
        editor: ScratchEditor
    ) {
        newList = editor.compile(
            lib,
            "newList",
            userAccessible = false
        ) {
            val returnArg = returnArg(PrimitiveType.Integer)
            val name = arg("name", PrimitiveType.Str)

            call(
                MemoryLib.alloc,
                4.sc, name, returnArg
            )
            val out = MemoryLib.heap[returnArg]

            MemoryLib.heap[out] = 0.sc //length
            MemoryLib.heap[out + 1.sc] = 1.sc //capacity
            MemoryLib.heap[out + 3.sc] = name //name
            call(
                MemoryLib.alloc,
                1.sc, "n".sc concat name, out + 2.sc //listPtr
            )
        }

        replace = if (CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
            compileInline(
                lib,
                "replace",
                parameters = mutableListOf(
                    Parameter("list", PrimitiveType.Integer),
                    Parameter("item", PrimitiveType.Integer),
                    Parameter("index", PrimitiveType.Integer)
                )
            ) {
                ListStatements.ReplaceItem(
                    list = MemoryLib.heap,
                    item = it[1],
                    index = OperatorExpressions.BinaryExpression(
                        left = ListExpressions.ItemAtIndex(
                            list = MemoryLib.heap,
                            index = OperatorExpressions.BinaryExpression(
                                left = it[0],
                                right = "2".scratch,
                                operator = OperatorExpressions.BinaryOperator.ADD
                            )
                        ),
                        right = it[2],
                        operator = OperatorExpressions.BinaryOperator.ADD
                    )
                )
            }
        } else {
            editor.compile(
                lib,
                "replace",
            ) {
                val list = arg("list", PrimitiveType.Integer)
                val item = arg("item", PrimitiveType.Integer)
                val index = arg("index", PrimitiveType.Integer)
                checkOutOfBounds(list, index)

                MemoryLib.heap[MemoryLib.heap[list + 2.sc] + index] = item
            }
        }

        remove = editor.compile(
            lib,
            "remove",
        ) {
            val list = arg("list", PrimitiveType.Integer)
            val index = arg("index", PrimitiveType.Integer)
            val length = MemoryLib.heap[list]
            val dataPtr = MemoryLib.heap[list + 2.sc]
            checkOutOfBounds(list, index)

            val shiftIndex = variable("list::shiftIndex")
            shiftIndex.set(index)

            control.repeat(length - (index + 1.sc)) {
                MemoryLib.heap[dataPtr + shiftIndex] = MemoryLib.heap[dataPtr + shiftIndex + 1.sc]
                shiftIndex.set(shiftIndex + 1.sc)
            }
            MemoryLib.heap[list] = length - 1.sc
        }

        itemAt = if (CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
            compileInline(
                lib,
                "itemAt",
                parameters = mutableListOf(
                    Parameter("list", PrimitiveType.Integer),
                    Parameter("index", PrimitiveType.Integer)
                ),
                returnType = PrimitiveType.Integer
            ) {
                ListExpressions.ItemAtIndex(
                    MemoryLib.heap,
                    OperatorExpressions.BinaryExpression(
                        left = ListExpressions.ItemAtIndex(
                            MemoryLib.heap,
                            OperatorExpressions.BinaryExpression(
                                left = it[0],
                                right = "2".scratch,
                                operator = OperatorExpressions.BinaryOperator.ADD
                            )
                        ),
                        right = it[1],
                        operator = OperatorExpressions.BinaryOperator.ADD
                    )
                )
            }
        } else {
            editor.compile(
                lib,
                "itemAt",
            ) {
                val list = arg("list", PrimitiveType.Integer)
                val index = arg("index", PrimitiveType.Integer)
                val returnVal = returnArg(PrimitiveType.Integer)
                checkOutOfBounds(list, index)

                MemoryLib.heap[returnVal] = MemoryLib.heap[MemoryLib.heap[list + 2.sc] + index]
            }
        }

        clear = compileInline(lib, "clear", mutableListOf(Parameter("list", PrimitiveType.Integer))) {
            ListStatements.ReplaceItem(
                MemoryLib.heap,
                item = "0".scratch,
                index = ListExpressions.ItemAtIndex(
                    MemoryLib.heap,
                    it[0]
                )
            )
        }

        length = compileInline(lib, "length", mutableListOf(Parameter("list", PrimitiveType.Integer)), returnType = PrimitiveType.Integer) {
            ListExpressions.ItemAtIndex(
                MemoryLib.heap,
                it[0]
            )
        }

        contains = editor.compile(
            lib,
            "contains",
        ) {
            val list = arg("list", PrimitiveType.Integer)
            val item = arg("item", PrimitiveType.Integer)
            val returnVal = returnArg(PrimitiveType.Integer)

            val length = MemoryLib.heap[list]
            val dataPtr = MemoryLib.heap[list + 2.sc]

            val found = variable("list::contains::found")
            found.set("false".sc)

            val searchIndex = variable("list::contains::index")
            searchIndex.set(0.sc)

            control.repeat(length) {
                control.ifThen(MemoryLib.heap[dataPtr + searchIndex] equals item) {
                    found.set("true".sc)
                }
                searchIndex.set(searchIndex + 1.sc)
            }

            MemoryLib.heap[returnVal] = found
        }

        reserve = editor.compile(
            lib,
            "reserve"
        ) {
            val list = arg("list", PrimitiveType.Integer)
            val newCapacity = arg("newCapacity", PrimitiveType.Integer)

            val length = MemoryLib.heap[list]
            val capacity = MemoryLib.heap[list + 1.sc]

            control.ifThen(newCapacity gt capacity) {
                val oldDataPtr = variable("list::reserve::oldData")
                oldDataPtr.set(MemoryLib.heap[list + 2.sc])

                val oldCapacity = variable("list::reserve::oldCapacity")
                oldCapacity.set(capacity)

                MemoryLib.heap[list + 1.sc] = newCapacity

                call(
                    MemoryLib.alloc,
                    newCapacity,
                    "n".sc concat MemoryLib.heap[list + 3.sc],
                    list + 2.sc
                )

                val copyIndex = variable("list::reserve::copyIndex")
                copyIndex.set(0.sc)
                control.repeat(length) {
                    MemoryLib.heap[MemoryLib.heap[list + 2.sc] + copyIndex] = MemoryLib.heap[oldDataPtr + copyIndex]
                    copyIndex.set(copyIndex + 1.sc)
                }

                call(
                    MemoryLib.free,
                    oldDataPtr,
                    oldCapacity
                )
            }
        }

        add = editor.compile(
            lib,
            "add",
        ) {
            val list = arg("list", PrimitiveType.Integer)
            val item = arg("item", PrimitiveType.Integer)

            val length = MemoryLib.heap[list]
            val capacity = MemoryLib.heap[list + 1.sc]

            control.ifThen(length equals capacity) {
                call(
                    reserve,
                    list,
                    capacity * 2.sc
                )
            }

            MemoryLib.heap[MemoryLib.heap[list + 2.sc] + length] = item
            MemoryLib.heap[list] = length + 1.sc
        }

        free = editor.compile(
            lib,
            "free",
            userAccessible = false
        ) {
            val list = arg("list", PrimitiveType.Integer)
            val capacity = MemoryLib.heap[list + 1.sc]
            val dataPtr = MemoryLib.heap[list + 2.sc]

            //dataPtr
            call(
                MemoryLib.free,
                dataPtr - 1.sc, capacity + 1.sc
            )

            //arr
            call(
                MemoryLib.free,
                list - 1.sc, 5.sc
            )
        }
    }

    private fun figureOutReachableStructs(out: MutableMap<ASTFile, List<Struct>>, ast: ASTFile) {
        if (out.containsKey(ast)) return
        out[ast] = ast.structs.map { it }
        ast.imports.forEach { (_, ast) -> figureOutReachableStructs(out, ast) }
    }

    fun getActualReturnType(context: CompilationContext, expr: CallExpression, check: (Expression) -> Type): Type {
        return when(expr.func) {
            newList -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::newList")
                val type = parseTypeFromString((expr.arguments[0] as StringLiteral).value, context)
                ListType(type)
            }
            itemAt -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::itemAt, requires 2 parameters")
                check(expr.arguments[1]).let {
                    if (it != PrimitiveType.Integer) {
                        throw TypeException(PrimitiveType.Integer, it, "list::itemAt called without an index?")
                    }
                }

                (check(expr.arguments[0]) as? ListType)?.elementType ?: throw GeneralCompilerException("list::itemAt called without a list")
            }
            add -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::add, requires 2 parameters")
                val list = check(expr.arguments[0])
                val added = check(expr.arguments[1])

                if(list !is ListType || list.elementType != added) {
                    throw GeneralCompilerException("Not adding to a list when calling list::add")
                }

                PrimitiveType.Void
            }
            remove -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::remove, requires 2 parameters")
                val list = check(expr.arguments[0])
                val index = check(expr.arguments[1])
                if(list !is ListType || index != PrimitiveType.Integer) {
                    throw GeneralCompilerException("Not passing a list to list::remove or not passing an integer")
                }
                PrimitiveType.Void
            }
            clear -> {
                if (expr.arguments.size != 1) throw GeneralCompilerException("Too many/little arguments on list::clear, requires 1 parameter")
                if (check(expr.arguments[0]) !is ListType) throw GeneralCompilerException("Not passing a list to list::clear")
                PrimitiveType.Void
            }
            length -> {
                if (expr.arguments.size != 1) throw GeneralCompilerException("Too many/little arguments on list::length, requires 1 parameter")
                if (check(expr.arguments[0]) !is ListType) throw GeneralCompilerException("Not passing a list to list::length")
                PrimitiveType.Integer
            }
            replace -> {
                if (expr.arguments.size != 3) throw GeneralCompilerException("Too many/little arguments on list::replace, requires 3 parameters")
                val list = check(expr.arguments[0])
                val added = check(expr.arguments[1])
                val index = check(expr.arguments[2])

                if(list !is ListType || list.elementType != added) {
                    throw GeneralCompilerException("Not adding to a list when calling list::replace")
                }
                if (index != PrimitiveType.Integer) {
                    throw TypeException(PrimitiveType.Integer, index, "Wrong type passed to list::replace")
                }

                PrimitiveType.Void
            }
            contains -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::contains, requires 2 parameters")
                val list = check(expr.arguments[0])
                val item = check(expr.arguments[1])

                if(list !is ListType || list.elementType != item) {
                    throw GeneralCompilerException("Comparing wrong type to list elements when calling list::contains")
                }

                PrimitiveType.Bool
            }
            reserve -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::reserve, requires 2 parameters")
                val list = check(expr.arguments[0])
                val capacity = check(expr.arguments[1])

                if (list !is ListType || capacity != PrimitiveType.Integer) {
                    throw GeneralCompilerException("Invalid arguments passed to list::reserve")
                }

                PrimitiveType.Void
            }
            free -> {
                if (expr.arguments.size != 1) throw GeneralCompilerException("Too many/little arguments on list::free, requires 1 parameter")
                if (check(expr.arguments[0]) !is ListType) throw GeneralCompilerException("Not passing a list to list::free")
                PrimitiveType.Void
            }
            else -> PrimitiveType.Null
        }
    }

    private fun parseTypeFromString(typeStr: String, context: CompilationContext): Type {
        if (typeStr.endsWith("[]")) {
            val innerTypeStr = typeStr.substring(0, typeStr.length - 2)
            return ListType(parseTypeFromString(innerTypeStr, context))
        }
        return context.types.find { it.toString() == typeStr }
            ?: throw NotFoundException("Type not found: $typeStr")
    }

    private fun CodeBuilder.checkOutOfBounds(
        list: DSLExpression,
        index: DSLExpression
    ) {
        val length = MemoryLib.heap[list]
        if(!CompilationConstants.DISABLE_INDEX_OUT_OF_BOUNDS) {
            control.ifThen(index gt (length - 1.sc)) {
                call(
                    ExceptionLib.panic,
                    "IndexOutOfBoundsException".sc
                )
            }

            control.ifThen(index lt 0.sc) {
                call(
                    ExceptionLib.panic,
                    "IndexOutOfBoundsException".sc
                )
            }
        }
    }
}