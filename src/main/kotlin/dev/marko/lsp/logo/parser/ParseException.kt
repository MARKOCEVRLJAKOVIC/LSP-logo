package dev.marko.lsp.logo.parser

/**
 * Thrown when the parser encounters a syntax error in a LOGO program.
 *
 * @property line   1-based line number where the error was detected.
 * @property column 1-based column number where the error was detected.
 */
class ParseException(
    message: String,
    val line: Int,
    val column: Int
) : Exception(message)
