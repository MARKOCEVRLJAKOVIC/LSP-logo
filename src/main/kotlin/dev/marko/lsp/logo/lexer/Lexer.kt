package dev.marko.lsp.logo.lexer

/**
 * Hand-written lexer (tokenizer) for the LOGO programming language.
 *
 * Design decisions:
 * - The lexer is a single-pass, character-by-character scanner.
 * - Keywords are resolved via a static map after reading an identifier,
 *   making it trivial to add new keywords later.
 * - Whitespace (spaces/tabs) is skipped, but newlines are emitted as
 *   [TokenType.NEWLINE] tokens so the parser can use them as statement
 *   separators.
 * - LOGO comments start with `;` and run to end-of-line; they are skipped
 *   entirely (no token emitted).
 * - Position tracking (line/column) is maintained for every token to
 *   support LSP diagnostics and hover information.
 *
 * @param source The complete LOGO source text to tokenize.
 */
class Lexer(private val source: String) {

    // Scanner state
    private var pos: Int = 0        // current index into [source]
    private var line: Int = 1       // 1-based current line
    private var column: Int = 1     // 1-based current column

    // Keyword lookup (case-insensitive)
    companion object {
        /** Maps uppercased keyword text - TokenType. */
        val KEYWORDS: Map<String, TokenType> = mapOf(
            "TO"      to TokenType.TO,
            "END"     to TokenType.END,
            "MAKE"    to TokenType.MAKE,
            "FORWARD" to TokenType.FORWARD,
            "RIGHT"   to TokenType.RIGHT,
            "LEFT"    to TokenType.LEFT,
            "REPEAT"  to TokenType.REPEAT,
        )
    }

    // Public API

    /**
     * Tokenizes the entire source string and returns an immutable list of
     * tokens, always terminated by an [TokenType.EOF] token.
     */
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()

        while (!isAtEnd()) {
            val c = peek()

            when {
                // Newlines
                c == '\n' -> {
                    tokens.add(makeToken(TokenType.NEWLINE, "\n"))
                    advance()
                    line++
                    column = 1
                }
                c == '\r' -> {
                    // Handle \r\n (Windows) and bare \r (old Mac)
                    advance()
                    val lexeme: String
                    if (!isAtEnd() && peek() == '\n') {
                        advance()
                        lexeme = "\r\n"
                    } else {
                        lexeme = "\r"
                    }
                    tokens.add(makeToken(TokenType.NEWLINE, lexeme, columnOffset = -(lexeme.length)))
                    line++
                    column = 1
                }

                // Whitespace (spaces, tabs) — skip
                c == ' ' || c == '\t' -> {
                    advance()
                }

                // Comments, skip to end of line
                c == ';' -> skipComment()

                // Single-character symbols
                c == '(' -> { tokens.add(makeToken(TokenType.LPAREN, "(")); advance() }
                c == ')' -> { tokens.add(makeToken(TokenType.RPAREN, ")")); advance() }
                c == '[' -> { tokens.add(makeToken(TokenType.LBRACKET, "[")); advance() }
                c == ']' -> { tokens.add(makeToken(TokenType.RBRACKET, "]")); advance() }
                c == '+' -> { tokens.add(makeToken(TokenType.PLUS, "+")); advance() }
                c == '-' -> { tokens.add(makeToken(TokenType.MINUS, "-")); advance() }
                c == '*' -> { tokens.add(makeToken(TokenType.STAR, "*")); advance() }
                c == '/' -> { tokens.add(makeToken(TokenType.SLASH, "/")); advance() }

                // Numbers
                c.isDigit() -> tokens.add(readNumber())

                // Variables (: followed by identifier)
                c == ':' -> tokens.add(readVariable())

                // Identifiers & keywords
                c.isLetter() -> tokens.add(readIdentifierOrKeyword())

                // Unknown character
                else -> {
                    tokens.add(makeToken(TokenType.UNKNOWN, c.toString()))
                    advance()
                }
            }
        }

        tokens.add(makeToken(TokenType.EOF, ""))
        return tokens
    }

    // Character-level helpers

    /** Returns the character at the current position without consuming it. */
    private fun peek(): Char = source[pos]

    /** Returns true when we have consumed all characters. */
    private fun isAtEnd(): Boolean = pos >= source.length

    /**
     * Consumes the current character and advances [pos] and [column].
     * Callers that handle newlines must reset [line]/[column] themselves.
     */
    private fun advance(): Char {
        val c = source[pos]
        pos++
        column++
        return c
    }

    // Token factories

    /**
     * Creates a token anchored at the *current* position (before any
     * consuming has happened for this token).
     *
     * @param columnOffset Optional adjustment — used when the token was
     *   already consumed before calling this (e.g. \r\n handling).
     */
    private fun makeToken(
        type: TokenType,
        lexeme: String,
        tokenLine: Int = line,
        columnOffset: Int = 0
    ): Token = Token(type, lexeme, tokenLine, column + columnOffset)

    // Multi-character token readers

    /**
     * Reads a contiguous sequence of digits and emits a [TokenType.NUMBER].
     * Currently only supports integer literals.
     */
    private fun readNumber(): Token {
        val startColumn = column
        val start = pos
        while (!isAtEnd() && peek().isDigit()) {
            advance()
        }
        val lexeme = source.substring(start, pos)
        return Token(TokenType.NUMBER, lexeme, line, startColumn)
    }

    /**
     * Reads an identifier (letter followed by letters/digits) and checks
     * whether it matches a LOGO keyword (case-insensitively).
     */
    private fun readIdentifierOrKeyword(): Token {
        val startColumn = column
        val start = pos
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_')) {
            advance()
        }
        val lexeme = source.substring(start, pos)
        val type = KEYWORDS[lexeme.uppercase()] ?: TokenType.IDENT
        return Token(type, lexeme, line, startColumn)
    }

    /**
     * Reads a LOGO variable: a `:` followed by an identifier.
     * If the colon is not followed by a valid identifier start, emits
     * [TokenType.UNKNOWN] for the bare colon.
     */
    private fun readVariable(): Token {
        val startColumn = column
        val start = pos
        advance() // consume ':'

        if (isAtEnd() || !peek().isLetter()) {
            // Bare colon with no identifier — treat as unknown
            return Token(TokenType.UNKNOWN, ":", line, startColumn)
        }

        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_')) {
            advance()
        }
        val lexeme = source.substring(start, pos)
        return Token(TokenType.VARIABLE, lexeme, line, startColumn)
    }

    /**
     * Skips a single-line comment that starts with `;`.
     * Advances past all characters until (but not including) the next newline.
     */
    private fun skipComment() {
        while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
            advance()
        }
    }
}
