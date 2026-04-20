package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.lexer.TokenType
import org.eclipse.lsp4j.SemanticTokens

/**
 * Provides LSP semantic tokens for LOGO source code.
 *
 * Uses the [Lexer] directly to tokenize source code and maps each
 * [TokenType] to one of the supported semantic token types. The result
 * is a [SemanticTokens] object with delta-encoded position data as
 * required by the LSP specification.
 *
 * ### Semantic token type legend (order matters — indices must match the legend
 * advertised in [dev.marko.lsp.logo.server.LogoLanguageServer]):
 *
 * | Index | LSP type   | LOGO elements                                |
 * |-------|------------|----------------------------------------------|
 * | 0     | keyword    | TO, END, MAKE, REPEAT, FOR, IF, IFELSE, etc. |
 * | 1     | function   | Built-in commands (FD, RT, …) and IDENT       |
 * | 2     | variable   | Variables (:x, :size)                         |
 * | 3     | number     | Numeric literals                              |
 * | 4     | string     | String literals ("hello)                      |
 * | 5     | comment    | Comments (; to end of line)                   |
 *
 * ### Delta encoding
 * Each token is encoded as 5 integers:
 * `[deltaLine, deltaStartChar, length, tokenTypeIndex, tokenModifiers]`
 *
 * - `deltaLine` = line difference from previous token (0 if same line)
 * - `deltaStartChar` = if same line → difference from previous start char;
 *   if new line → absolute 0-based start position
 * - `length` = length of the token in characters
 * - `tokenTypeIndex` = index into the legend's token types list
 * - `tokenModifiers` = always 0 (no modifiers used)
 */
class SemanticTokensProvider {

    companion object {
        /** Index constants matching the legend order. */
        const val TYPE_KEYWORD  = 0
        const val TYPE_FUNCTION = 1
        const val TYPE_VARIABLE = 2
        const val TYPE_NUMBER   = 3
        const val TYPE_STRING   = 4
        const val TYPE_COMMENT  = 5

        const val MOD_DEFAULT_LIBRARY = 0
    }

    /**
     * Tokenizes [source] and returns a [SemanticTokens] object with
     * delta-encoded token data.
     *
     * Never throws — returns an empty [SemanticTokens] on any error.
     */
    fun provide(source: String): SemanticTokens {
        return try {
            val tokens = Lexer(source).tokenize()
            val data = mutableListOf<Int>()

            var prevLine = 0   // 0-based
            var prevChar = 0   // 0-based

            for (token in tokens) {
                val (typeIndex, modifiers) = mapToken(token.type) ?: continue

                // Convert 1-based lexer positions to 0-based LSP positions
                val tokenLine = token.line - 1
                val tokenChar = token.column - 1
                val length = token.lexeme.length

                val deltaLine = tokenLine - prevLine
                val deltaStartChar = if (deltaLine == 0) {
                    tokenChar - prevChar
                } else {
                    tokenChar // absolute 0-based position on new line
                }

                data.add(deltaLine)
                data.add(deltaStartChar)
                data.add(length)
                data.add(typeIndex)
                data.add(modifiers) // no modifiers

                prevLine = tokenLine
                prevChar = tokenChar
            }

            SemanticTokens(data)
        } catch (_: Exception) {
            SemanticTokens(emptyList())
        }
    }

    /**
     * Maps a [TokenType] to the corresponding semantic token type index,
     * or `null` if the token should not be emitted (e.g. NEWLINE, EOF, UNKNOWN).
     */
    private fun mapToken(type: TokenType): Pair<Int, Int>? {
        return when (type) {
            // Keywords
            TokenType.TO,
            TokenType.END,
            TokenType.MAKE,
            TokenType.REPEAT,
            TokenType.FOR,
            TokenType.IF,
            TokenType.IFELSE,
            TokenType.DO_WHILE,
            TokenType.STOP,
            TokenType.OUTPUT -> TYPE_KEYWORD to 0


            // Functions (built-in commands and user-defined procedure calls)
            TokenType.BUILTIN -> TYPE_FUNCTION to (1 shl MOD_DEFAULT_LIBRARY)
            TokenType.IDENT -> TYPE_FUNCTION to 0

            TokenType.VARIABLE -> TYPE_VARIABLE to 0
            TokenType.NUMBER   -> TYPE_NUMBER   to 0
            TokenType.STRING   -> TYPE_STRING   to 0
            TokenType.COMMENT  -> TYPE_COMMENT  to 0

            // Tokens that do not get semantic highlighting
            TokenType.NEWLINE,
            TokenType.EOF,
            TokenType.UNKNOWN,
            TokenType.LPAREN,
            TokenType.RPAREN,
            TokenType.LBRACKET,
            TokenType.RBRACKET,
            TokenType.PLUS,
            TokenType.MINUS,
            TokenType.STAR,
            TokenType.SLASH,
            TokenType.LESS,
            TokenType.GREATER,
            TokenType.EQUAL -> null
        }
    }
}