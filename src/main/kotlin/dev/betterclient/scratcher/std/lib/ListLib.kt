package dev.betterclient.scratcher.std.lib

import dev.betterclient.scratcher.CompilationConstants
import dev.betterclient.scratcher.ast.ASTFile
import dev.betterclient.scratcher.ast.CallExpression
import dev.betterclient.scratcher.ast.Expression
import dev.betterclient.scratcher.ast.StandardLibASTFunction
import dev.betterclient.scratcher.ast.StringLiteral
import dev.betterclient.scratcher.ast.Struct
import dev.betterclient.scratcher.ast.Type
import dev.betterclient.scratcher.ast.parser.CompilationContext
import dev.betterclient.scratcher.codegen.ScratchEditor
import dev.betterclient.scratcher.except.GeneralCompilerException
import dev.betterclient.scratcher.except.NotFoundException
import dev.betterclient.scratcher.except.TypeException
import dev.betterclient.scratcher.std.dsl.CodeBuilder
import dev.betterclient.scratcher.std.dsl.DSLExpression
import dev.betterclient.scratcher.std.dsl.compile
import dev.betterclient.scratcher.std.dsl.equals
import dev.betterclient.scratcher.std.dsl.gt
import dev.betterclient.scratcher.std.dsl.lt
import dev.betterclient.scratcher.std.dsl.minus
import dev.betterclient.scratcher.std.dsl.plus
import dev.betterclient.scratcher.std.dsl.times

object ListLib {
    lateinit var newList: StandardLibASTFunction
    lateinit var add: StandardLibASTFunction
    lateinit var remove: StandardLibASTFunction
    lateinit var itemAt: StandardLibASTFunction
    lateinit var clear: StandardLibASTFunction
    lateinit var length: StandardLibASTFunction
    lateinit var replace: StandardLibASTFunction

    val listFuncs by lazy {
        listOf(newList, add, remove, itemAt, clear, length, replace)
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
            val returnArg = returnArg(Type.int)
            val name = arg("name", Type.str)

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
                1.sc, out + 2.sc, "n$name".sc //listPtr
            )
        }

        add = editor.compile(
            lib,
            "add",
        ) {
            val oldDataPtr = variable("list::oldData")
            val list = arg("list", Type.int)
            val item = arg("item", Type.int)

            val length = MemoryLib.heap[list]
            val capacity = MemoryLib.heap[list + 1.sc]

            control.ifThen(length equals capacity) {
                MemoryLib.heap[list + 1.sc] = capacity * 2.sc
                oldDataPtr.set(MemoryLib.heap[list + 2.sc])
                call(
                    MemoryLib.alloc,
                    capacity, MemoryLib.heap[list + 3.sc], list + 2.sc
                )
                val copyIndex = variable("list::copyIndex")
                copyIndex.set(0.sc)
                control.repeat(length) {
                    MemoryLib.heap[MemoryLib.heap[list + 2.sc] + copyIndex] = MemoryLib.heap[oldDataPtr + copyIndex]
                    copyIndex.set(copyIndex + 1.sc)
                }
                call(
                    MemoryLib.free,
                    oldDataPtr, length
                )
            }

            MemoryLib.heap[MemoryLib.heap[list + 2.sc] + length] = item
            MemoryLib.heap[list] = length + 1.sc
        }

        replace = editor.compile(
            lib,
            "replace",
        ) {
            val list = arg("list", Type.int)
            val item = arg("item", Type.int)
            val index = arg("index", Type.int)
            checkOutOfBounds(list, index)

            MemoryLib.heap[MemoryLib.heap[list + 2.sc] + index] = item
        }

        remove = editor.compile(
            lib,
            "remove",
        ) {
            val list = arg("list", Type.int)
            val index = arg("index", Type.int)
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

        itemAt = editor.compile(
            lib,
            "itemAt",
        ) {
            val list = arg("list", Type.int)
            val index = arg("index", Type.int)
            val returnVal = returnArg(Type.int)
            checkOutOfBounds(list, index)

            MemoryLib.heap[returnVal] = MemoryLib.heap[MemoryLib.heap[list + 2.sc] + index]
        }

        clear = editor.compile(
            lib,
            "clear",
        ) {
            val list = arg("list", Type.int)
            MemoryLib.heap[list] = 0.sc
        }

        length = editor.compile(
            lib, "length"
        ) {
            val list = arg("list", Type.int)
            val returnArg = returnArg(Type.int)
            MemoryLib.heap[returnArg] = MemoryLib.heap[list]
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
                val type = (expr.arguments[0] as StringLiteral).value.let { typeStr ->
                    context.types.find { it.toString() == typeStr }?: throw NotFoundException("Type not found: $typeStr")
                }
                type.list()
            }
            itemAt -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::itemAt, requires 2 parameters")
                check(expr.arguments[1]).let {
                    if (it != Type.int) {
                        throw TypeException(Type.int, it, "list::itemAt called without an index?")
                    }
                }

                check(expr.arguments[0]).inner?: throw GeneralCompilerException("list::itemAt called without a list")
            }
            add -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::add, requires 2 parameters")
                val list = check(expr.arguments[0])
                val added = check(expr.arguments[1])

                if(list.inner != added) {
                    throw GeneralCompilerException("Not adding to a list when calling list::add")
                }

                Type.void
            }
            remove -> {
                if (expr.arguments.size != 2) throw GeneralCompilerException("Too many/little arguments on list::remove, requires 2 parameters")
                val list = check(expr.arguments[0])
                val index = check(expr.arguments[1])
                if(list.inner == null || index != Type.int) {
                    throw GeneralCompilerException("Not passing a list to list::remove or not passing an integer")
                }
                Type.void
            }
            clear -> {
                if (expr.arguments.size != 1) throw GeneralCompilerException("Too many/little arguments on list::clear, requires 1 parameter")
                if (check(expr.arguments[0]).inner == null) throw GeneralCompilerException("Not passing a list to list::clear")
                Type.void
            }
            length -> {
                if (expr.arguments.size != 1) throw GeneralCompilerException("Too many/little arguments on list::length, requires 1 parameter")
                if (check(expr.arguments[0]).inner == null) throw GeneralCompilerException("Not passing a list to list::length")
                Type.int
            }
            replace -> {
                if (expr.arguments.size != 3) throw GeneralCompilerException("Too many/little arguments on list::replace, requires 3 parameters")
                val list = check(expr.arguments[0])
                val added = check(expr.arguments[1])
                val index = check(expr.arguments[2])

                if(list.inner != added) {
                    throw GeneralCompilerException("Not adding to a list when calling list::replace")
                }
                if (index != Type.int) {
                    throw TypeException(Type.int, index, "Wrong type passed to list::replace")
                }

                Type.void
            }
            else -> Type.nullType
        }
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