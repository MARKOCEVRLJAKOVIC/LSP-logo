package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.parser.Parser
import dev.marko.lsp.logo.parser.ProgramNode
import org.eclipse.lsp4j.SymbolKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DocumentSymbolProvider].
 *
 * All LSP positions in the returned [org.eclipse.lsp4j.DocumentSymbol]
 * entries are **0-based**.
 */
class DocumentSymbolProviderTest {

    private val provider = DocumentSymbolProvider()

    /** Helper: lex + parse LOGO source into a [ProgramNode]. */
    private fun parse(source: String): ProgramNode {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens).parseProgram()
    }

    // Procedures

    @Test
    fun `single procedure produces Function symbol with correct param count`() {
        val program = parse("TO square :size\n  FD :size\nEND")
        val symbols = provider.provide(program)

        assertEquals(1, symbols.size)
        val sym = symbols[0]
        assertEquals("square", sym.name)
        assertEquals(SymbolKind.Function, sym.kind)
        assertTrue(sym.detail.contains("1 params"), "detail should mention 1 params")
    }

    @Test
    fun `procedure with no params shows 0 params`() {
        val program = parse("TO greet\n  PRINT \"hi\nEND")
        val symbols = provider.provide(program)

        assertEquals(1, symbols.size)
        assertTrue(symbols[0].detail.contains("0 params"))
    }

    // Variables

    @Test
    fun `top-level MAKE produces Variable symbol`() {
        val program = parse("MAKE \"x 10\nFD :x")
        val symbols = provider.provide(program)

        assertEquals(1, symbols.size)
        val sym = symbols[0]
        assertEquals("x", sym.name)
        assertEquals(SymbolKind.Variable, sym.kind)
        assertEquals("variable", sym.detail)
    }

    // Mixed

    @Test
    fun `multiple mixed symbols are returned with correct kinds`() {
        val program = parse("TO square :size\n  FD :size\nEND\nMAKE \"x 10")
        val symbols = provider.provide(program)

        assertEquals(2, symbols.size)
        assertEquals(SymbolKind.Function, symbols[0].kind)
        assertEquals(SymbolKind.Variable, symbols[1].kind)
    }

    // Empty

    @Test
    fun `empty program returns empty list`() {
        val program = parse("")
        val symbols = provider.provide(program)

        assertTrue(symbols.isEmpty())
    }

    // MAKE inside procedure body is excluded

    @Test
    fun `MAKE inside procedure body is not included in top-level symbols`() {
        val program = parse("TO setup\n  MAKE \"x 10\nEND")
        val symbols = provider.provide(program)

        assertEquals(1, symbols.size, "Only the procedure itself should appear")
        assertEquals(SymbolKind.Function, symbols[0].kind)
    }

    // 0-based position

    @Test
    fun `position is converted to 0-based LSP convention`() {
        val program = parse("TO square :size\nEND")
        val symbols = provider.provide(program)

        assertEquals(1, symbols.size)
        val range = symbols[0].range
        assertEquals(0, range.start.line, "1-based line 1 should become 0")
        assertEquals(0, range.start.character, "1-based column 1 should become 0")
    }
}
