package dev.marko.lsp.logo.lexer

/**
 * Represents a single token produced by the [Lexer].
 *
 * Using a data class gives us structural equality, destructuring, and a
 * readable [toString] for free — all useful during testing and debugging.
 *
 * @property type   The category of this token.
 * @property lexeme The raw source text that was matched.
 * @property line   1-based line number where the token starts.
 * @property column 1-based column number where the token starts.
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val line: Int,
    val column: Int
)
