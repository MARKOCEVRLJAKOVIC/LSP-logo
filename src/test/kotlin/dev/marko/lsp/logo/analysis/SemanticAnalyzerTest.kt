package dev.marko.lsp.logo.analysis

import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.parser.Parser
import dev.marko.lsp.logo.parser.ProgramNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SemanticAnalyzer].
 *
 * Each test follows the same pattern:
 *   1. Lex + parse a LOGO source snippet.
 *   2. Run the semantic analyzer.
 *   3. Assert on the resulting error list.
 */
class SemanticAnalyzerTest {

    // Helper

    /** Parses [source] and returns the semantic errors produced by analysis. */
    private fun analyzeSource(source: String): List<SemanticError> {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parseProgram()
        return SemanticAnalyzer().analyze(program)
    }

    // 1. Valid procedure definition & call

    @Test
    fun `valid procedure is registered and called correctly`() {
        val src = """
            TO square :size
              REPEAT 4 [FD :size RT 90]
            END
            square 100
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    // 2. Undefined procedure call

    @Test
    fun `calling undefined procedure produces an error`() {
        val src = """
            triangle 100
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Undefined procedure"))
        assertTrue(errors[0].message.contains("triangle"))
    }

    // 3. Wrong argument count

    @Test
    fun `wrong argument count produces an error`() {
        val src = """
            TO square :size
              REPEAT 4 [FD :size RT 90]
            END
            square 100 200
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("expects 1"))
        assertTrue(errors[0].message.contains("got 2"))
    }

    @Test
    fun `too few arguments produces an error`() {
        val src = """
            TO box :width :height
              FD :width RT 90 FD :height RT 90
            END
            box 100
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("expects 2"))
        assertTrue(errors[0].message.contains("got 1"))
    }

    // 4. Undefined variable

    @Test
    fun `referencing undefined variable produces an error`() {
        val src = """
            FD :unknown
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Undefined variable"))
        assertTrue(errors[0].message.contains("unknown"))
    }

    // 5. Variable defined via MAKE

    @Test
    fun `variable defined with MAKE is visible`() {
        val src = """
            MAKE "x 42
            FD :x
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    // 6. Procedure parameters visible inside body

    @Test
    fun `procedure parameters are visible inside the body`() {
        val src = """
            TO move :dist :angle
              FD :dist
              RT :angle
            END
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    @Test
    fun `procedure parameter not visible outside body`() {
        val src = """
            TO move :dist
              FD :dist
            END
            FD :dist
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Undefined variable"))
    }

    // 7. FOR loop variable visible in body

    @Test
    fun `FOR loop variable is visible inside loop body`() {
        val src = """
            FOR [i 1 10 1] [FD :i]
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    @Test
    fun `FOR loop variable is NOT visible outside loop body`() {
        val src = """
            FOR [i 1 10 1] [FD :i]
            FD :i
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Undefined variable"))
    }

    // 8. Nested procedures

    @Test
    fun `nested procedure definitions are both registered`() {
        val src = """
            TO outer :x
              TO inner :y
                FD :y
              END
              inner :x
            END
            outer 100
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    // 9. Built-in commands never produce errors

    @Test
    fun `builtin commands are always valid`() {
        val src = """
            FD 100
            BK 50
            RT 90
            LT 45
            PENUP
            PENDOWN
            HOME
            CLEARSCREEN
            PRINT 42
            SETCOLOR 1
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors for builtins but got: $errors")
    }

    // 10. Case insensitivity

    @Test
    fun `procedure lookup is case-insensitive`() {
        val src = """
            TO MyProc :val
              FD :val
            END
            myproc 50
            MYPROC 75
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    @Test
    fun `variable lookup is case-insensitive`() {
        val src = """
            MAKE "Greeting 42
            FD :greeting
            FD :GREETING
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    // 11. Multiple errors collected at once

    @Test
    fun `multiple errors are collected at once`() {
        val src = """
            unknownProc 1
            FD :noSuchVar
            anotherBadCall 2
        """.trimIndent()

        val errors = analyzeSource(src)
        assertTrue(errors.size >= 3, "Expected at least 3 errors but got ${errors.size}: $errors")
    }

    // 12. Forward reference to procedure

    @Test
    fun `forward reference to procedure is valid`() {
        val src = """
            myproc 100
            TO myproc :x
              FD :x
            END
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    // 13. Error positions are accurate

    @Test
    fun `error positions point to the correct location`() {
        val src = """
            FD 100
            badcall 1
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(1, errors.size)
        assertEquals(2, errors[0].line, "Error should be on line 2")
        assertEquals(1, errors[0].column, "Error should be at column 1")
    }

    // 14. MAKE inside procedure body

    @Test
    fun `MAKE inside procedure body creates a local variable`() {
        val src = """
            TO demo
              MAKE "local 10
              FD :local
            END
            demo
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }

    // 15. Complex program

    @Test
    fun `complex program with mixed constructs has no errors`() {
        val src = """
            MAKE "sides 4
            TO polygon :n :len
              REPEAT :n [FD :len RT 360 / :n]
            END
            TO spiral :count
              FOR [i 1 :count 1] [polygon :sides :i]
            END
            spiral 10
        """.trimIndent()

        val errors = analyzeSource(src)
        assertEquals(0, errors.size, "Expected no errors but got: $errors")
    }
}
