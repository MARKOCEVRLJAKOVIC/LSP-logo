package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.analysis.SemanticAnalyzer
import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.parser.Parser
import dev.marko.lsp.logo.parser.ProgramNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HoverProvider].
 *
 * All cursor positions are **0-based** (LSP convention).
 * Hover text displays positions as 1-based (human-readable).
 */
class HoverProviderTest {

    private val provider = HoverProvider()

    /** Helper: parse and analyse LOGO source, returning the AST and analyzer. */
    private fun parseAndAnalyze(source: String): Pair<ProgramNode, SemanticAnalyzer> {
        val tokens = Lexer(source).tokenize()
        val program = Parser(tokens).parseProgram()
        val analyzer = SemanticAnalyzer()
        analyzer.analyze(program)
        return program to analyzer
    }

    // User-defined procedure

    @Test
    fun `hover over user-defined procedure call shows param count and definition line`() {
        val source = "TO SQUARE :side\n  FD :side\nEND\nSQUARE 50"
        val (program, analyzer) = parseAndAnalyze(source)

        // "SQUARE" call is on line 3 (0-based), column 0
        val hover = provider.provide(program, analyzer, 3, 0)

        assertNotNull(hover)
        val content = hover!!.contents.right.value
        assertTrue(content.contains("SQUARE"), "Should contain procedure name")
        assertTrue(content.contains("1"), "Should contain param count '1'")
        assertTrue(content.contains("line 1"), "Should contain 1-based definition line")
    }

    // Built-in command

    @Test
    fun `hover over built-in command shows built-in description`() {
        val source = "FD 100"
        val (program, analyzer) = parseAndAnalyze(source)

        // "FD" is on line 0, column 0
        val hover = provider.provide(program, analyzer, 0, 0)

        assertNotNull(hover)
        val content = hover!!.contents.right.value
        assertTrue(content.contains("built-in LOGO command"), "Should indicate built-in")
    }

    // Variable

    @Test
    fun `hover over variable shows name and definition location`() {
        val source = "MAKE \"x 10\nFD :x"
        val (program, analyzer) = parseAndAnalyze(source)

        // ":x" on line 1, column 3
        val hover = provider.provide(program, analyzer, 1, 3)

        assertNotNull(hover)
        val content = hover!!.contents.right.value
        assertTrue(content.contains("X"), "Should contain variable name")
        assertTrue(content.contains("line 1"), "Should contain 1-based definition line")
    }

    // Number literal → null

    @Test
    fun `hover over number literal returns null`() {
        val source = "FD 100"
        val (program, analyzer) = parseAndAnalyze(source)

        // "100" starts at line 0, column 3
        val hover = provider.provide(program, analyzer, 0, 3)

        assertNull(hover)
    }

    // No symbol → null

    @Test
    fun `hover over position with no symbol returns null`() {
        val source = "FD 100"
        val (program, analyzer) = parseAndAnalyze(source)

        // Column 20 is way past any token
        val hover = provider.provide(program, analyzer, 0, 20)

        assertNull(hover)
    }
}
