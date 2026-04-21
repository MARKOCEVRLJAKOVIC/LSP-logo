package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.parser.Parser
import dev.marko.lsp.logo.parser.ProgramNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CursorResolver].
 *
 * All cursor positions in tests are **0-based** (LSP convention).
 * The resolver internally converts to 1-based AST positions.
 */
class CursorResolverTest {

    private val resolver = CursorResolver()

    /** Helper: parse LOGO source into a [ProgramNode]. */
    private fun parse(source: String): ProgramNode {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens).parseProgram()
    }

    // Procedure call tests

    @Test
    fun `cursor on procedure name in call resolves to ResolvedProc`() {
        // Source:  "TO BOX\nEND\nBOX"
        // Line 2 (0-based): "BOX" starts at column 0
        val program = parse("TO BOX\nEND\nBOX")
        val result = resolver.resolve(program, 2, 0)
        assertNotNull(result)
        assertTrue(result is ResolvedSymbol.ResolvedProc)
        assertEquals("BOX", (result as ResolvedSymbol.ResolvedProc).name)
    }

    @Test
    fun `cursor at end of procedure name in call resolves to ResolvedProc`() {
        val program = parse("TO BOX\nEND\nBOX")
        // "BOX" occupies columns 0,1,2 → column 2 is still inside
        val result = resolver.resolve(program, 2, 2)
        assertNotNull(result)
        assertTrue(result is ResolvedSymbol.ResolvedProc)
    }

    @Test
    fun `cursor just past procedure name returns null`() {
        val program = parse("TO BOX\nEND\nBOX")
        // column 3 is one past "BOX"
        val result = resolver.resolve(program, 2, 3)
        assertNull(result)
    }

    // Variable reference tests

    @Test
    fun `cursor on variable reference resolves to ResolvedVar`() {
        // Source: "MAKE \"size 100\nFD :size"
        // Line 1 (0-based): ":size" starts at column 3
        val program = parse("MAKE \"size 100\nFD :size")
        val result = resolver.resolve(program, 1, 3)
        assertNotNull(result)
        assertTrue(result is ResolvedSymbol.ResolvedVar)
        assertEquals("size", (result as ResolvedSymbol.ResolvedVar).name)
    }

    @Test
    fun `cursor on colon of variable resolves to ResolvedVar`() {
        val program = parse("MAKE \"size 100\nFD :size")
        // ':' is at column 3
        val result = resolver.resolve(program, 1, 3)
        assertNotNull(result)
        assertTrue(result is ResolvedSymbol.ResolvedVar)
    }

    @Test
    fun `cursor on last char of variable name resolves to ResolvedVar`() {
        val program = parse("MAKE \"size 100\nFD :size")
        // ":size" occupies columns 3,4,5,6,7 → column 7 is 'e'
        val result = resolver.resolve(program, 1, 7)
        assertNotNull(result)
        assertTrue(result is ResolvedSymbol.ResolvedVar)
    }

    // Keyword tests

    @Test
    fun `cursor on REPEAT keyword returns null`() {
        val program = parse("REPEAT 4 [FD 100]")
        // "REPEAT" is at line 0, col 0 — it's a keyword, not a symbol
        val result = resolver.resolve(program, 0, 0)
        assertNull(result)
    }

    @Test
    fun `cursor on TO keyword returns null`() {
        val program = parse("TO BOX\nFD 100\nEND")
        // "TO" is at line 0, col 0
        val result = resolver.resolve(program, 0, 0)
        assertNull(result)
    }

    // Number tests

    @Test
    fun `cursor on number literal returns null`() {
        val program = parse("FD 100")
        // "100" starts at column 3 on line 0
        val result = resolver.resolve(program, 0, 3)
        assertNull(result)
    }

    // Out of range tests

    @Test
    fun `cursor on empty line returns null`() {
        val program = parse("FD 100")
        // Line 5 does not exist
        val result = resolver.resolve(program, 5, 0)
        assertNull(result)
    }

    @Test
    fun `cursor beyond any symbol column returns null`() {
        val program = parse("FD 100")
        // Column 20 is far beyond any token
        val result = resolver.resolve(program, 0, 20)
        assertNull(result)
    }
}
