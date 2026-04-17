package dev.marko.lsp.logo.lexer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LexerTest {

    // Helper

    /** Tokenize and strip the trailing EOF for easier assertions. */
    private fun tokenize(source: String): List<Token> =
        Lexer(source).tokenize().dropLast(1)

    /** Tokenize and return types only (excluding EOF). */
    private fun types(source: String): List<TokenType> =
        tokenize(source).map { it.type }

    // Empty / EOF

    @Test
    fun `empty input produces only EOF`() {
        val tokens = Lexer("").tokenize()
        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
        assertEquals(1, tokens[0].line)
        assertEquals(1, tokens[0].column)
    }

    // Keywords (case-insensitive)

    @Test
    fun `keywords recognized in uppercase`() {
        assertEquals(
            listOf(TokenType.TO, TokenType.END, TokenType.MAKE, TokenType.FORWARD,
                   TokenType.RIGHT, TokenType.LEFT, TokenType.REPEAT),
            types("TO END MAKE FORWARD RIGHT LEFT REPEAT")
        )
    }

    @Test
    fun `keywords recognized in lowercase`() {
        assertEquals(
            listOf(TokenType.TO, TokenType.END, TokenType.FORWARD),
            types("to end forward")
        )
    }

    @Test
    fun `keywords recognized in mixed case`() {
        assertEquals(
            listOf(TokenType.REPEAT, TokenType.MAKE),
            types("RePeAt mAkE")
        )
    }

    // Numbers

    @Test
    fun `single digit number`() {
        val tokens = tokenize("5")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.NUMBER, tokens[0].type)
        assertEquals("5", tokens[0].lexeme)
    }

    @Test
    fun `multi-digit number`() {
        val tokens = tokenize("42")
        assertEquals("42", tokens[0].lexeme)
        assertEquals(TokenType.NUMBER, tokens[0].type)
    }

    @Test
    fun `multiple numbers separated by space`() {
        val tokens = tokenize("10 200 3")
        assertEquals(3, tokens.size)
        assertEquals(listOf("10", "200", "3"), tokens.map { it.lexeme })
    }

    // Identifiers

    @Test
    fun `simple identifier`() {
        val tokens = tokenize("square")
        // "square" is not a keyword
        assertEquals(TokenType.IDENT, tokens[0].type)
        assertEquals("square", tokens[0].lexeme)
    }

    @Test
    fun `identifier with digits`() {
        val tokens = tokenize("proc1")
        assertEquals(TokenType.IDENT, tokens[0].type)
        assertEquals("proc1", tokens[0].lexeme)
    }

    @Test
    fun `identifier with underscores`() {
        val tokens = tokenize("my_proc")
        assertEquals(TokenType.IDENT, tokens[0].type)
        assertEquals("my_proc", tokens[0].lexeme)
    }

    // Variables

    @Test
    fun `variable with single letter`() {
        val tokens = tokenize(":x")
        assertEquals(TokenType.VARIABLE, tokens[0].type)
        assertEquals(":x", tokens[0].lexeme)
    }

    @Test
    fun `variable with full name`() {
        val tokens = tokenize(":size")
        assertEquals(TokenType.VARIABLE, tokens[0].type)
        assertEquals(":size", tokens[0].lexeme)
    }

    @Test
    fun `bare colon is unknown`() {
        val tokens = tokenize(": ")
        assertEquals(TokenType.UNKNOWN, tokens[0].type)
        assertEquals(":", tokens[0].lexeme)
    }

    @Test
    fun `colon at end of input is unknown`() {
        val tokens = tokenize(":")
        assertEquals(TokenType.UNKNOWN, tokens[0].type)
    }

    // Symbols & Operators

    @Test
    fun `parentheses`() {
        assertEquals(
            listOf(TokenType.LPAREN, TokenType.RPAREN),
            types("()")
        )
    }

    @Test
    fun `brackets`() {
        assertEquals(
            listOf(TokenType.LBRACKET, TokenType.RBRACKET),
            types("[]")
        )
    }

    @Test
    fun `arithmetic operators`() {
        assertEquals(
            listOf(TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH),
            types("+ - * /")
        )
    }

    // Comments

    @Test
    fun `comment is skipped entirely`() {
        val tokens = tokenize("; this is a comment")
        assertTrue(tokens.isEmpty(), "Comment should produce no tokens")
    }

    @Test
    fun `comment after code`() {
        val tokens = tokenize("FORWARD 100 ; move forward")
        assertEquals(
            listOf(TokenType.FORWARD, TokenType.NUMBER),
            tokens.map { it.type }
        )
    }

    // Newlines

    @Test
    fun `newline produces NEWLINE token`() {
        val tokens = tokenize("TO\nEND")
        assertEquals(
            listOf(TokenType.TO, TokenType.NEWLINE, TokenType.END),
            tokens.map { it.type }
        )
    }

    // Line & Column tracking

    @Test
    fun `single line column tracking`() {
        // "TO square"
        // columns: T=1, s=4
        val tokens = tokenize("TO square")
        assertEquals(1, tokens[0].column)  // TO
        assertEquals(4, tokens[1].column)  // square
    }

    @Test
    fun `multi-line tracking`() {
        val source = "TO square\nFORWARD 100\nEND"
        val tokens = tokenize(source)
        // Line 1: TO(1,1) square(1,4) \n
        // Line 2: FORWARD(2,1) 100(2,9) \n
        // Line 3: END(3,1)
        val forward = tokens.first { it.type == TokenType.FORWARD }
        assertEquals(2, forward.line)
        assertEquals(1, forward.column)

        val number = tokens.first { it.type == TokenType.NUMBER }
        assertEquals(2, number.line)
        assertEquals(9, number.column)

        val end = tokens.first { it.type == TokenType.END }
        assertEquals(3, end.line)
        assertEquals(1, end.column)
    }

    // Unknown characters

    @Test
    fun `unknown character produces UNKNOWN token`() {
        val tokens = tokenize("@")
        assertEquals(TokenType.UNKNOWN, tokens[0].type)
        assertEquals("@", tokens[0].lexeme)
    }

    @Test
    fun `unknown among valid tokens`() {
        val tokens = tokenize("FORWARD @ 100")
        assertEquals(
            listOf(TokenType.FORWARD, TokenType.UNKNOWN, TokenType.NUMBER),
            tokens.map { it.type }
        )
    }

    // Full program

    @Test
    fun `full LOGO program`() {
        val source = """
            TO square :size
              REPEAT 4 [
                FORWARD :size
                RIGHT 90
              ]
            END
        """.trimIndent()

        val tokens = tokenize(source)
        val typeList = tokens.filter { it.type != TokenType.NEWLINE }.map { it.type }

        assertEquals(
            listOf(
                TokenType.TO, TokenType.IDENT, TokenType.VARIABLE,       // TO square :size
                TokenType.REPEAT, TokenType.NUMBER, TokenType.LBRACKET,  // REPEAT 4 [
                TokenType.FORWARD, TokenType.VARIABLE,                    // FORWARD :size
                TokenType.RIGHT, TokenType.NUMBER,                        // RIGHT 90
                TokenType.RBRACKET,                                       // ]
                TokenType.END                                             // END
            ),
            typeList
        )
    }

    // Expression tokenization

    @Test
    fun `arithmetic expression`() {
        val tokens = tokenize("FORWARD :x + 10")
        assertEquals(
            listOf(TokenType.FORWARD, TokenType.VARIABLE, TokenType.PLUS, TokenType.NUMBER),
            tokens.map { it.type }
        )
    }

    @Test
    fun `expression with parentheses`() {
        val tokens = tokenize("FORWARD (:x + :y) * 2")
        assertEquals(
            listOf(
                TokenType.FORWARD, TokenType.LPAREN, TokenType.VARIABLE,
                TokenType.PLUS, TokenType.VARIABLE, TokenType.RPAREN,
                TokenType.STAR, TokenType.NUMBER
            ),
            tokens.map { it.type }
        )
    }
}
