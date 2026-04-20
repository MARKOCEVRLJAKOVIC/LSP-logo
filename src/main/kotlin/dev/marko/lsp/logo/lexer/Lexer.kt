package dev.marko.lsp.logo.lexer

/**
 * Hand-written lexer (tokenizer) for the LOGO programming language.
 *
 * Design decisions:
 * - The lexer is a single-pass, character-by-character scanner.
 * - **Two-tier identifier resolution**: after reading an identifier the lexer
 *   first checks the [KEYWORDS] map (true keywords that affect parsing),
 *   then the [BUILTINS] set (built-in procedures emitted as [TokenType.BUILTIN]),
 *   and falls back to [TokenType.IDENT].
 * - LOGO string literals start with a single `"` and extend until the next
 *   whitespace or end of input (e.g. `"hello`).  They are emitted as
 *   [TokenType.STRING].
 * - Comparison operators `<`, `>`, `=` are emitted as [TokenType.LESS],
 *   [TokenType.GREATER], [TokenType.EQUAL].
 * - Whitespace (spaces/tabs) is skipped, but newlines are emitted as
 *   [TokenType.NEWLINE] tokens so the parser can use them as statement
 *   separators.
 * - LOGO comments start with `;` and run to end-of-line; they are emitted
 *   as [TokenType.COMMENT] tokens for semantic highlighting.
 * - Position tracking (line/column) is maintained for every token to
 *   support LSP diagnostics and hover information.
 *
 * @param source The complete LOGO source text to tokenize.
 */
class Lexer(private val source: String) {

    //  Scanner state 
    private var pos: Int = 0        // current index into [source]
    private var line: Int = 1       // 1-based current line
    private var column: Int = 1     // 1-based current column

    //  Keyword & built-in lookup 
    companion object {
        /**
         * Maps uppercased keyword text → [TokenType].
         *
         * Only **true keywords** that influence parsing structure belong here.
         */
        val KEYWORDS: Map<String, TokenType> = mapOf(
            "TO"       to TokenType.TO,
            "END"      to TokenType.END,
            "MAKE"     to TokenType.MAKE,
            "REPEAT"   to TokenType.REPEAT,
            "FOR"      to TokenType.FOR,
            "IF"       to TokenType.IF,
            "IFELSE"   to TokenType.IFELSE,
            "DO.WHILE" to TokenType.DO_WHILE,
            "STOP"     to TokenType.STOP,
            "OUTPUT"   to TokenType.OUTPUT,
        )

        /**
         * Set of uppercased names for all known LOGO built-in procedures and
         * their shorthand aliases.
         *
         * The parser treats every [TokenType.BUILTIN] uniformly; the specific
         * command is distinguished by the token's lexeme at a later stage.
         */
        val BUILTINS: Set<String> = setOf(
            //  Turtle movement 
            "FORWARD", "FD",
            "BACK", "BK",
            "RIGHT", "RT",
            "LEFT", "LT",

            //  Pen control 
            "PENUP", "PU",
            "PENDOWN", "PD",

            //  Screen / turtle 
            "CLEARSCREEN", "CS",
            "HOME",
            "SETX", "SETY", "SETXY",
            "SETHEADING", "SETH",
            "HIDETURTLE", "HT",
            "SHOWTURTLE", "ST",
            "CHANGESHAPE", "CSH",

            //  Appearance 
            "SETCOLOR",
            "SETWIDTH",

            //  Drawing 
            "ARC",
            "FILL",
            "FILLED",
            "LABEL",
            "SETLABELHEIGHT",

            //  I/O 
            "PRINT",
            "TYPE",
            "READWORD",

            //  Queries 
            "POS",
            "XCOR", "YCOR",
            "HEADING",
            "TOWARDS",

            //  Math / misc 
            "RANDOM",
            "SUM",
            "DIFFERENCE",
            "RUN",
            "WAIT",

            //  Window mode 
            "WINDOW",
            "FENCE",

            //  List / word operations 
            "FIRST", "LAST",
            "ITEM", "PICK",
            "BUTFIRST", "BF",
            "BUTLAST", "BL",
            "LIST",

            //  Predicates 
            "NOTEQUALP",
            "EQUALP",
        )
    }

    //  Public API 

    /**
     * Tokenizes the entire source string and returns an immutable list of
     * tokens, always terminated by an [TokenType.EOF] token.
     */
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()

        while (!isAtEnd()) {
            val c = peek()

            when {
                //  Newlines 
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

                //  Whitespace (spaces, tabs) — skip 
                c == ' ' || c == '\t' -> {
                    advance()
                }

                //  Comments — tokenize to end of line 
                c == ';' -> tokens.add(readComment())

                //  Single-character symbols 
                c == '(' -> { tokens.add(makeToken(TokenType.LPAREN, "(")); advance() }
                c == ')' -> { tokens.add(makeToken(TokenType.RPAREN, ")")); advance() }
                c == '[' -> { tokens.add(makeToken(TokenType.LBRACKET, "[")); advance() }
                c == ']' -> { tokens.add(makeToken(TokenType.RBRACKET, "]")); advance() }
                c == '+' -> { tokens.add(makeToken(TokenType.PLUS, "+")); advance() }
                c == '-' -> { tokens.add(makeToken(TokenType.MINUS, "-")); advance() }
                c == '*' -> { tokens.add(makeToken(TokenType.STAR, "*")); advance() }
                c == '/' -> { tokens.add(makeToken(TokenType.SLASH, "/")); advance() }

                //  Comparison operators 
                c == '<' -> { tokens.add(makeToken(TokenType.LESS, "<")); advance() }
                c == '>' -> { tokens.add(makeToken(TokenType.GREATER, ">")); advance() }
                c == '=' -> { tokens.add(makeToken(TokenType.EQUAL, "=")); advance() }

                //  Numbers 
                c.isDigit() -> tokens.add(readNumber())

                //  String literals ("hello) 
                c == '"' -> tokens.add(readString())

                //  Variables (: followed by identifier) 
                c == ':' -> tokens.add(readVariable())

                //  Identifiers, keywords & built-ins 
                c.isLetter() -> tokens.add(readIdentifierOrKeyword())

                //  Unknown character 
                else -> {
                    tokens.add(makeToken(TokenType.UNKNOWN, c.toString()))
                    advance()
                }
            }
        }

        tokens.add(makeToken(TokenType.EOF, ""))
        return tokens
    }

    //  Character-level helpers 

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

    //  Token factories 

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

    //  Multi-character token readers 

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
     * Reads a LOGO string literal.
     *
     * In LOGO, strings start with a single `"` and extend until the next
     * whitespace character, structural delimiter (`[`, `]`, `(`, `)`), or
     * end of input.  For example, `"hello` produces a token with lexeme
     * `"hello` and type [TokenType.STRING].
     */
    private fun readString(): Token {
        val startColumn = column
        val start = pos
        advance() // consume the opening "
        while (!isAtEnd() && !peek().isWhitespace() && peek() !in "[]()") {
            advance()
        }
        val lexeme = source.substring(start, pos)
        return Token(TokenType.STRING, lexeme, line, startColumn)
    }

    /**
     * Reads an identifier (letter followed by letters, digits, dots, or
     * underscores) and resolves it in order:
     *
     * 1. [KEYWORDS] map → specific keyword [TokenType]
     * 2. [BUILTINS] set → [TokenType.BUILTIN]
     * 3. Otherwise     → [TokenType.IDENT]
     */
    private fun readIdentifierOrKeyword(): Token {
        val startColumn = column
        val start = pos
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_' || peek() == '.')) {
            advance()
        }
        val lexeme = source.substring(start, pos)
        val upper = lexeme.uppercase()
        val type = KEYWORDS[upper]
            ?: if (upper in BUILTINS) TokenType.BUILTIN else TokenType.IDENT
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
     * Reads a single-line comment that starts with `;`.
     * Captures all characters until (but not including) the next newline
     * and returns a [TokenType.COMMENT] token.
     */
    private fun readComment(): Token {
        val startColumn = column
        val start = pos

        while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
            advance()
        }

        val lexeme = source.substring(start, pos)
        return Token(TokenType.COMMENT, lexeme, line, startColumn)
    }
}
