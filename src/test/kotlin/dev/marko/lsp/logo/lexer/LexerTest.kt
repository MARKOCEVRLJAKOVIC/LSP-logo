package dev.marko.lsp.logo.lexer

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LexerTest {

    //  Helper 

    /** Tokenize and strip the trailing EOF for easier assertions. */
    private fun tokenize(source: String): List<Token> =
        Lexer(source).tokenize().dropLast(1)

    /** Tokenize and return types only (excluding EOF). */
    private fun types(source: String): List<TokenType> =
        tokenize(source).map { it.type }

    //  Empty / EOF 

    @Test
    fun `empty input produces only EOF`() {
        val tokens = Lexer("").tokenize()
        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
        assertEquals(1, tokens[0].line)
        assertEquals(1, tokens[0].column)
    }

    //  True keywords (case-insensitive) 

    @Test
    fun `true keywords recognized in uppercase`() {
        assertEquals(
            listOf(
                TokenType.TO, TokenType.END, TokenType.MAKE,
                TokenType.REPEAT, TokenType.FOR, TokenType.IF,
                TokenType.IFELSE, TokenType.STOP, TokenType.OUTPUT
            ),
            types("TO END MAKE REPEAT FOR IF IFELSE STOP OUTPUT")
        )
    }

    @Test
    fun `DO_WHILE keyword with dot`() {
        assertEquals(
            listOf(TokenType.DO_WHILE),
            types("do.while")
        )
    }

    @Test
    fun `keywords recognized in lowercase`() {
        assertEquals(
            listOf(TokenType.TO, TokenType.END, TokenType.REPEAT),
            types("to end repeat")
        )
    }

    @Test
    fun `keywords recognized in mixed case`() {
        assertEquals(
            listOf(TokenType.REPEAT, TokenType.MAKE),
            types("RePeAt mAkE")
        )
    }

    //  Built-in procedures 

    @Test
    fun `built-in commands produce BUILTIN token`() {
        assertEquals(
            listOf(TokenType.BUILTIN, TokenType.BUILTIN, TokenType.BUILTIN),
            types("FORWARD RIGHT LEFT")
        )
    }

    @Test
    fun `built-in shorthand aliases`() {
        assertEquals(
            listOf(TokenType.BUILTIN, TokenType.BUILTIN, TokenType.BUILTIN, TokenType.BUILTIN),
            types("FD RT LT BK")
        )
    }

    @Test
    fun `built-ins are case-insensitive`() {
        assertEquals(
            listOf(TokenType.BUILTIN, TokenType.BUILTIN),
            types("forward Fd")
        )
    }

    @Test
    fun `built-in preserves original lexeme`() {
        val tokens = tokenize("forward")
        assertEquals("forward", tokens[0].lexeme)
        assertEquals(TokenType.BUILTIN, tokens[0].type)
    }

    @Test
    fun `all built-in categories recognized`() {
        // One representative from each category
        val builtins = listOf(
            "PENUP", "PU", "CLEARSCREEN", "CS", "HOME",
            "SETX", "SETY", "SETXY", "SETHEADING", "SETH",
            "HIDETURTLE", "HT", "SHOWTURTLE", "ST",
            "CHANGESHAPE", "CSH", "SETCOLOR", "SETWIDTH",
            "ARC", "FILL", "FILLED", "LABEL", "SETLABELHEIGHT",
            "PRINT", "TYPE", "READWORD",
            "POS", "XCOR", "YCOR", "HEADING", "TOWARDS",
            "RANDOM", "SUM", "DIFFERENCE", "RUN", "WAIT",
            "WINDOW", "FENCE",
            "FIRST", "LAST", "ITEM", "PICK",
            "BUTFIRST", "BF", "BUTLAST", "BL", "LIST",
            "NOTEQUALP", "EQUALP"
        )
        for (name in builtins) {
            val result = types(name)
            assertEquals(
                listOf(TokenType.BUILTIN), result,
                "Expected BUILTIN for '$name' but got $result"
            )
        }
    }

    //  Numbers 

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

    //  String literals 

    @Test
    fun `simple string literal`() {
        val tokens = tokenize("\"hello")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("\"hello", tokens[0].lexeme)
    }

    @Test
    fun `string literal stops at whitespace`() {
        val tokens = tokenize("\"red \"blue")
        assertEquals(2, tokens.size)
        assertEquals(TokenType.STRING, tokens[0].type)
        assertEquals("\"red", tokens[0].lexeme)
        assertEquals("\"blue", tokens[1].lexeme)
    }

    @Test
    fun `string literal in context`() {
        assertEquals(
            listOf(TokenType.BUILTIN, TokenType.STRING),
            types("PRINT \"hello")
        )
    }

    @Test
    fun `string literal column tracking`() {
        val tokens = tokenize("PRINT \"hello")
        assertEquals(7, tokens[1].column) // "hello starts at column 7
    }

    //  Identifiers 

    @Test
    fun `simple identifier`() {
        val tokens = tokenize("square")
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

    //  Variables 

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

    //  Symbols & Operators 

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

    //  Comparison operators 

    @Test
    fun `comparison operators`() {
        assertEquals(
            listOf(TokenType.LESS, TokenType.GREATER, TokenType.EQUAL),
            types("< > =")
        )
    }

    @Test
    fun `comparison in conditional expression`() {
        assertEquals(
            listOf(TokenType.IF, TokenType.VARIABLE, TokenType.LESS, TokenType.NUMBER),
            types("IF :x < 10")
        )
    }

    //  Comments 

    @Test
    fun `comment produces COMMENT token`() {
        val tokens = tokenize("; this is a comment")
        assertEquals(1, tokens.size, "Comment should produce one COMMENT token")
        assertEquals(TokenType.COMMENT, tokens[0].type)
        assertEquals("; this is a comment", tokens[0].lexeme)
        assertEquals(1, tokens[0].line)
        assertEquals(1, tokens[0].column)
    }

    @Test
    fun `comment after code`() {
        val tokens = tokenize("FORWARD 100 ; move forward")
        assertEquals(
            listOf(TokenType.BUILTIN, TokenType.NUMBER, TokenType.COMMENT),
            tokens.map { it.type }
        )
        assertEquals("; move forward", tokens[2].lexeme)
    }

    //  Newlines 

    @Test
    fun `newline produces NEWLINE token`() {
        val tokens = tokenize("TO\nEND")
        assertEquals(
            listOf(TokenType.TO, TokenType.NEWLINE, TokenType.END),
            tokens.map { it.type }
        )
    }

    //  Line & Column tracking 

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
        val forward = tokens.first { it.type == TokenType.BUILTIN }
        assertEquals(2, forward.line)
        assertEquals(1, forward.column)

        val number = tokens.first { it.type == TokenType.NUMBER }
        assertEquals(2, number.line)
        assertEquals(9, number.column)

        val end = tokens.first { it.type == TokenType.END }
        assertEquals(3, end.line)
        assertEquals(1, end.column)
    }

    //  Unknown characters 

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
            listOf(TokenType.BUILTIN, TokenType.UNKNOWN, TokenType.NUMBER),
            tokens.map { it.type }
        )
    }

    //  Full program 

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
                TokenType.TO, TokenType.IDENT, TokenType.VARIABLE,          // TO square :size
                TokenType.REPEAT, TokenType.NUMBER, TokenType.LBRACKET,     // REPEAT 4 [
                TokenType.BUILTIN, TokenType.VARIABLE,                       // FORWARD :size
                TokenType.BUILTIN, TokenType.NUMBER,                         // RIGHT 90
                TokenType.RBRACKET,                                          // ]
                TokenType.END                                                // END
            ),
            typeList
        )
    }

    //  Expression tokenization 

    @Test
    fun `arithmetic expression`() {
        val tokens = tokenize("FORWARD :x + 10")
        assertEquals(
            listOf(TokenType.BUILTIN, TokenType.VARIABLE, TokenType.PLUS, TokenType.NUMBER),
            tokens.map { it.type }
        )
    }

    @Test
    fun `expression with parentheses`() {
        val tokens = tokenize("FORWARD (:x + :y) * 2")
        assertEquals(
            listOf(
                TokenType.BUILTIN, TokenType.LPAREN, TokenType.VARIABLE,
                TokenType.PLUS, TokenType.VARIABLE, TokenType.RPAREN,
                TokenType.STAR, TokenType.NUMBER
            ),
            tokens.map { it.type }
        )
    }

    //  Two-tier resolution 

    @Test
    fun `keyword takes priority over builtin`() {
        // MAKE is both conceptually a "command" but it's a true keyword
        assertEquals(listOf(TokenType.MAKE), types("MAKE"))
    }

    @Test
    fun `unknown identifier is IDENT not BUILTIN`() {
        assertEquals(listOf(TokenType.IDENT), types("myFunction"))
    }

    @Test
    fun `LOGO program with strings and comparisons`() {
        val source = """IF :x < 10 [PRINT "small]"""
        val tokens = tokenize(source)
        assertEquals(
            listOf(
                TokenType.IF, TokenType.VARIABLE, TokenType.LESS, TokenType.NUMBER,
                TokenType.LBRACKET, TokenType.BUILTIN, TokenType.STRING, TokenType.RBRACKET
            ),
            tokens.map { it.type }
        )
    }
}
