package dev.betterclient.scratcher.ast

sealed class CompilerException(message: String) : Exception(message)

//user to blame
class GeneralCompilerException(message: String) : CompilerException(message)

//compiler is to blame, not the code
class TypeException(expected: Type, found: Type, message: String) : CompilerException("$message, expected $expected, found $found")
class NotImplementedException(message: String) : CompilerException(message)
class UnreachableException(message: String? = null) : CompilerException("Unreachable, $message")