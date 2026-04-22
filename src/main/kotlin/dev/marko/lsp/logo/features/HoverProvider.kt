package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.analysis.SemanticAnalyzer
import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.parser.ProgramNode
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind

/**
 * Provides hover information for symbols in a LOGO program.
 *
 * For **user-defined procedures** the hover shows the procedure name,
 * parameter count, and where it was defined (1-based line/column).
 * For **built-in commands** it shows a short description.
 * For **variables** it shows the variable name and its definition location.
 *
 * Internally creates its own [CursorResolver] instance (stateless, no
 * injection needed).
 */
class HoverProvider {

    /** Stateless cursor resolver — safe to reuse across calls. */
    private val cursorResolver = CursorResolver()

    /**
     * Returns hover information for the symbol at the given 0-based LSP
     * position, or `null` if there is no recognisable symbol at that location.
     *
     * @param program   The root AST node of the document.
     * @param analyzer  The semantic analyzer that has already analysed [program].
     * @param line      0-based line number (LSP convention).
     * @param character 0-based column number (LSP convention).
     * @return A [Hover] with Markdown content, or `null`.
     */
    fun provide(
        program: ProgramNode,
        analyzer: SemanticAnalyzer,
        line: Int,
        character: Int
    ): Hover? {
        val resolved = cursorResolver.resolve(program, line, character) ?: return null

        val markdown: String = when (resolved) {
            is ResolvedSymbol.ResolvedProc -> hoverForProc(resolved.name, analyzer)
            is ResolvedSymbol.ResolvedVar  -> hoverForVar(resolved.name, analyzer)
        } ?: return null

        return Hover(MarkupContent(MarkupKind.MARKDOWN, markdown))
    }

    // Hover text builders

    private fun hoverForProc(name: String, analyzer: SemanticAnalyzer): String? {
        val symbol = analyzer.symbolTable.lookupProc(name)
        if (symbol != null) {
            return """
                |**Procedure** `${symbol.name}`
                |
                |**Parameters:** ${symbol.paramCount}
                |**Defined at:** line ${symbol.position.line}, column ${symbol.position.column}
            """.trimMargin()
        }

        // Check if it is a built-in command
        if (name.uppercase() in Lexer.BUILTINS) {
            return "**`${name.uppercase()}`** built-in LOGO command"
        }

        return null
    }

    private fun hoverForVar(name: String, analyzer: SemanticAnalyzer): String? {
        val symbol = analyzer.symbolTable.lookupVar(name) ?: return null
        return """
            |**Variable** `${symbol.name}`
            |
            |**Defined at:** line ${symbol.position.line}, column ${symbol.position.column}
        """.trimMargin()
    }
}
