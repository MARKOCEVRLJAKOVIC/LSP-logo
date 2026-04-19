package dev.marko.lsp.logo.analysis

/**
 * Represents a single semantic error detected during analysis.
 *
 * Unlike [dev.marko.lsp.logo.parser.ParseException], semantic errors do not
 * interrupt analysis — they are collected into a list so every problem can
 * be reported to the user at once (e.g. via LSP diagnostics).
 *
 * @property message  Human-readable description of the error.
 * @property line     1-based source line where the error was detected.
 * @property column   1-based source column where the error was detected.
 */
data class SemanticError(
    val message: String,
    val line: Int,
    val column: Int
)
