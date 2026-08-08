package dev.betterclient.scratcher.ast

sealed class CompilerException(message: String) : Exception(message)

//user to blame
class GeneralCompilerException(message: String) : CompilerException(message)
class NotFoundException(message: String) : CompilerException(message)
class VoidVariableException(message: String) : CompilerException(message)
class DuplicateDefinitionException(message: String) : CompilerException(message)
class TypeException(expected: Type, found: Type, message: String) : CompilerException("$message, expected $expected, found $found")
class NotNullableException(message: String) : CompilerException(message)
class TypeAnalysisException(message: String) : CompilerException(message)

//compiler is to blame, not the code
class NotImplementedException(message: String) : CompilerException(message)
class UnreachableException(message: String? = null) : CompilerException("Unreachable, $message")