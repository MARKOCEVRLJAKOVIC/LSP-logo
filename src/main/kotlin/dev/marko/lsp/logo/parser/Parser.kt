package dev.marko.lsp.logo.parser

import dev.marko.lsp.logo.lexer.Token
import dev.marko.lsp.logo.lexer.TokenType

/**
 * Recursive-descent parser for the LOGO programming language.
 *
 * Transforms a flat list of [Token]s produced by the lexer into an AST
 * rooted at [ProgramNode]. Parsing errors are collected in [errors] so
 * that downstream consumers (e.g. LSP diagnostics) can report them all
 * at once rather than stopping at the first failure.
 *
 * @property tokens The complete token stream, expected to end with [TokenType.EOF].
 */
class Parser(private val tokens: List<Token>) {

    /** Current position in the token stream. */
    private var pos: Int = 0

    /** Accumulated parse errors encountered during parsing. */
    val errors: MutableList<ParseException> = mutableListOf()

    // Helper methods

    /**
     * Returns the token at the current position without consuming it.
     *
     * Safe to call at any time — when [pos] is past the end of the list
     * the last token (EOF) is returned.
     */
    private fun peek(): Token = tokens[pos]

    /**
     * Returns the token at the current position and advances [pos] by one.
     */
    private fun advance(): Token = tokens[pos++]

    /**
     * Returns `true` if the current token is [TokenType.EOF],
     * indicating there are no more meaningful tokens to consume.
     */
    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    /**
     * Returns `true` if the current token's type matches [type]
     * without consuming it.
     */
    private fun check(type: TokenType): Boolean = peek().type == type

    /**
     * Consumes and returns the current token if its type matches [type].
     *
     * @throws ParseException if the current token does not match.
     */
    private fun expect(type: TokenType): Token {
        if (check(type)) return advance()
        throw ParseException(
            "Expected $type but found '${peek().lexeme}'",
            peek().line,
            peek().column
        )
    }

    /**
     * Consumes consecutive [TokenType.NEWLINE] and [TokenType.COMMENT]
     * tokens, effectively skipping blank lines and comments between
     * statements.
     */
    private fun skipNewlines() {
        while (check(TokenType.NEWLINE) || check(TokenType.COMMENT)) advance()
    }

    /**
     * Returns a [Position] corresponding to the current token's
     * source location. Used to tag AST nodes with their origin.
     */
    private fun currentPosition(): Position = Position(peek().line, peek().column)

    // Top-level entry point

    /**
     * Parses the entire token stream into a [ProgramNode].
     *
     * Collects top-level statements (procedure definitions, commands, etc.)
     * separated by newlines. Parsing continues until [TokenType.EOF] is reached.
     */
    fun parseProgram(): ProgramNode {
        val position = currentPosition()
        skipNewlines()
        val statements = mutableListOf<Node>()

        while (!isAtEnd()) {
            val node = parseStatement()
            if (node != null) statements += node
            skipNewlines()
        }

        return ProgramNode(statements, position)
    }

    // Statement dispatch

    /**
     * Parses a single statement by dispatching on the current token type.
     *
     * On error the parser records the [ParseException], skips tokens until the
     * next [TokenType.NEWLINE] or [TokenType.EOF], and then returns `null` to
     * the caller (which must handle nulls accordingly).
     */
    private fun parseStatement(): Node? {
        skipNewlines()

        return try {
            when (peek().type) {
                TokenType.TO       -> parseProcedureDef()
                TokenType.REPEAT   -> parseRepeat()
                TokenType.FOR      -> parseFor()
                TokenType.MAKE     -> parseMake()
                TokenType.IF       -> parseIf()
                TokenType.IFELSE   -> parseIfElse()
                TokenType.DO_WHILE -> parseDoWhile()
                TokenType.STOP     -> parseStop()
                TokenType.OUTPUT   -> parseOutput()
                TokenType.BUILTIN  -> parseBuiltinCall()
                TokenType.IDENT    -> parseUserCall()
                else -> throw ParseException(
                    "Unexpected token: ${peek().lexeme}",
                    peek().line,
                    peek().column
                )
            }
        } catch (e: ParseException) {
            errors += e
            // Skip tokens until the next statement boundary
            while (!isAtEnd() && !check(TokenType.NEWLINE)) {
                advance()
            }
            null
        }
    }

    // Procedure definition

    /**
     * Parses a procedure definition of the form:
     * ```
     * TO name :param1 :param2 …
     *   <body statements>
     * END
     * ```
     *
     * Parameters are collected as long as consecutive [TokenType.VARIABLE]
     * tokens appear after the procedure name. The leading `:` is stripped
     * from each parameter name (e.g. `:size` → `"size"`).
     *
     * Body statements are parsed until [TokenType.END] or [TokenType.EOF]
     * is reached. A final [expect] call ensures the closing `END` keyword
     * is present.
     */
    private fun parseProcedureDef(): ProcedureDefNode {
        val position = currentPosition()
        expect(TokenType.TO)

        val name = expect(TokenType.IDENT).lexeme

        val params = mutableListOf<String>()
        while (check(TokenType.VARIABLE)) {
            params += advance().lexeme.removePrefix(":")
        }

        skipNewlines()

        val body = mutableListOf<Node>()
        while (!check(TokenType.END) && !isAtEnd()) {
            val node = parseStatement()
            if (node != null) body += node
            skipNewlines()
        }

        expect(TokenType.END)

        return ProcedureDefNode(name, params, body, position)
    }

    // Control flow

    /**
     * ```
     * REPEAT expr [ block ]
     * ```
     */
    private fun parseRepeat(): RepeatNode {
        val position = currentPosition()
        expect(TokenType.REPEAT)
        val count = parseExpr()
        val body = parseBlock()
        return RepeatNode(count, body, position)
    }

    /**
     * ```
     * FOR [ IDENT NUMBER NUMBER NUMBER? ] [ block ]
     * ```
     */
    private fun parseFor(): ForNode {
        val position = currentPosition()
        expect(TokenType.FOR)

        expect(TokenType.LBRACKET)
        val variable = expect(TokenType.IDENT).lexeme
        val start = parseExpr()
        val end = parseExpr()
        val step = if (!check(TokenType.RBRACKET)) parseExpr() else NumberNode(1.0, currentPosition())
        expect(TokenType.RBRACKET)

        val body = parseBlock()
        return ForNode(variable, start, end, step, body, position)
    }

    /**
     * ```
     * MAKE "varName expr
     * ```
     */
    private fun parseMake(): MakeNode {
        val position = currentPosition()
        expect(TokenType.MAKE)
        val nameToken = expect(TokenType.STRING)
        // Strip the leading " from the string literal (e.g. "x → x)
        val varName = nameToken.lexeme.removePrefix("\"")
        val value = parseExpr()
        return MakeNode(varName, value, position)
    }

    /**
     * ```
     * IF expr [ block ]
     * ```
     */
    private fun parseIf(): IfNode {
        val position = currentPosition()
        expect(TokenType.IF)
        val condition = parseExpr()
        val body = parseBlock()
        return IfNode(condition, body, position)
    }

    /**
     * ```
     * IFELSE expr [ thenBlock ] [ elseBlock ]
     * ```
     */
    private fun parseIfElse(): IfElseNode {
        val position = currentPosition()
        expect(TokenType.IFELSE)
        val condition = parseExpr()
        val thenBody = parseBlock()
        val elseBody = parseBlock()
        return IfElseNode(condition, thenBody, elseBody, position)
    }

    /**
     * ```
     * DO.WHILE [ block ] expr
     * ```
     */
    private fun parseDoWhile(): DoWhileNode {
        val position = currentPosition()
        expect(TokenType.DO_WHILE)
        val body = parseBlock()
        val condition = parseExpr()
        return DoWhileNode(body, condition, position)
    }

    /**
     * ```
     * STOP
     * ```
     */
    private fun parseStop(): StopNode {
        val position = currentPosition()
        expect(TokenType.STOP)
        return StopNode(position)
    }

    /**
     * ```
     * OUTPUT expr
     * ```
     */
    private fun parseOutput(): OutputNode {
        val position = currentPosition()
        expect(TokenType.OUTPUT)
        val value = parseExpr()
        return OutputNode(value, position)
    }

    // Procedure calls

    /**
     * Parses a built-in procedure call (e.g. `FORWARD 50`).
     *
     * Arguments are consumed greedily: as long as the next token can start
     * an expression and is not a statement keyword or block boundary, it is
     * parsed as an argument.
     */
    private fun parseBuiltinCall(): ProcedureCallNode {
        val position = currentPosition()
        val name = advance().lexeme
        val args = parseArguments()
        return ProcedureCallNode(name, args, position)
    }

    /**
     * Parses a user-defined procedure call (e.g. `square 100`).
     */
    private fun parseUserCall(): ProcedureCallNode {
        val position = currentPosition()
        val name = advance().lexeme
        val args = parseArguments()
        return ProcedureCallNode(name, args, position)
    }

    /**
     * Greedily consumes arguments for a procedure call.
     *
     * An expression is considered an argument if the next token is one of:
     * [NUMBER], [STRING], [VARIABLE], [LPAREN], [BUILTIN], [IDENT].
     *
     * Consumption stops at: [NEWLINE], [EOF], [RBRACKET], [RPAREN], or any
     * top-level keyword ([TO], [END], [REPEAT], [FOR], [MAKE], [IF],
     * [IFELSE], [DO_WHILE], [STOP], [OUTPUT]).
     */
    private fun parseArguments(): List<Node> {
        val args = mutableListOf<Node>()
        while (isArgStart()) {
            args += parseExpr()
        }
        return args
    }

    /**
     * Returns `true` if the current token can begin an argument expression.
     */
    private fun isArgStart(): Boolean = peek().type in ARG_START_TOKENS

    companion object {
        /** Token types that can begin an argument expression. */
        private val ARG_START_TOKENS = setOf(
            TokenType.NUMBER,
            TokenType.STRING,
            TokenType.VARIABLE,
            TokenType.LPAREN,
        )

        /** Token types for binary operators. */
        private val BINARY_OP_TOKENS = setOf(
            TokenType.PLUS,
            TokenType.MINUS,
            TokenType.STAR,
            TokenType.SLASH,
            TokenType.LESS,
            TokenType.GREATER,
            TokenType.EQUAL,
        )
    }

    // Expressions

    /**
     * Parses a flat binary expression (no operator precedence):
     * ```
     * parseAtom() (PLUS|MINUS|STAR|SLASH|LESS|GREATER|EQUAL parseAtom())*
     * ```
     *
     * Builds a left-associative chain of [BinaryExprNode]s using the
     * [BinaryOp] enum.
     */
    private fun parseExpr(): Node {
        var left = parseAtom()
        while (peek().type in BINARY_OP_TOKENS) {
            val opToken = advance()
            val op = tokenTypeToBinaryOp(opToken.type)
            val right = parseAtom()
            left = BinaryExprNode(op, left, right, left.position)
        }
        return left
    }

    /**
     * Maps a [TokenType] for an operator to the corresponding [BinaryOp].
     */
    private fun tokenTypeToBinaryOp(type: TokenType): BinaryOp = when (type) {
        TokenType.PLUS    -> BinaryOp.PLUS
        TokenType.MINUS   -> BinaryOp.MINUS
        TokenType.STAR    -> BinaryOp.STAR
        TokenType.SLASH   -> BinaryOp.SLASH
        TokenType.LESS    -> BinaryOp.LESS
        TokenType.GREATER -> BinaryOp.GREATER
        TokenType.EQUAL   -> BinaryOp.EQUAL
        else -> throw ParseException("Unknown operator: ${type}", peek().line, peek().column)
    }

    /**
     * Parses an atomic expression:
     * ```
     * NUMBER | STRING | VARIABLE | LPAREN expr RPAREN | builtinCall | userCall
     * ```
     */
    private fun parseAtom(): Node {
        return when (peek().type) {
            TokenType.NUMBER -> {
                val token = advance()
                NumberNode(token.lexeme.toDouble(), Position(token.line, token.column))
            }
            TokenType.STRING -> {
                val token = advance()
                StringNode(token.lexeme.removePrefix("\""), Position(token.line, token.column))
            }
            TokenType.VARIABLE -> {
                val token = advance()
                VariableNode(token.lexeme.removePrefix(":"), Position(token.line, token.column))
            }
            TokenType.LPAREN -> {
                advance() // consume '('
                val expr = parseExpr()
                expect(TokenType.RPAREN)
                expr
            }
            TokenType.BUILTIN -> parseBuiltinCall()
            TokenType.IDENT   -> parseUserCall()
            else -> throw ParseException(
                "Expected expression but found '${peek().lexeme}'",
                peek().line,
                peek().column
            )
        }
    }

    // Block

    /**
     * Parses a bracketed block of statements:
     * ```
     * [ statement* ]
     * ```
     * Newlines inside the block are skipped.
     */
    private fun parseBlock(): List<Node> {
        expect(TokenType.LBRACKET)
        skipNewlines()
        val statements = mutableListOf<Node>()
        while (!check(TokenType.RBRACKET) && !isAtEnd()) {
            val node = parseStatement()
            if (node != null) statements += node
            skipNewlines()
        }
        expect(TokenType.RBRACKET)
        return statements
    }
}
