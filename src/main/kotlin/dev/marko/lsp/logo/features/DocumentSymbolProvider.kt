package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.parser.*
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind

/**
 * Produces a flat list of [DocumentSymbol] entries for a LOGO program.
 *
 * Walks the **top-level** statements of a [ProgramNode] and emits:
 * - [SymbolKind.Function] for each [ProcedureDefNode]
 * - [SymbolKind.Variable] for each top-level [MakeNode] or [ForNode] loop variable
 *
 * Nested symbols inside procedure bodies are intentionally excluded.
 */
class DocumentSymbolProvider {

    /**
     * Returns the document symbols found in [program].
     *
     * Positions are converted from the 1-based AST convention to the 0-based
     * LSP convention via `(n - 1).coerceAtLeast(0)`.
     *
     * @param program The parsed AST root.
     * @return A flat list of [DocumentSymbol] entries.
     */
    fun provide(program: ProgramNode): List<DocumentSymbol> {
        val symbols = mutableListOf<DocumentSymbol>()

        for (statement in program.statements) {
            when (statement) {
                is ProcedureDefNode -> symbols.add(procedureSymbol(statement))
                is MakeNode -> symbols.add(variableSymbol(statement.varName, statement.position))
                is ForNode -> symbols.add(variableSymbol(statement.variable, statement.position))
                else -> { /* skip other top-level statements */ }
            }
        }

        return symbols
    }

    // Symbol builders

    private fun procedureSymbol(node: ProcedureDefNode): DocumentSymbol {
        val lspPos = toLspPosition(node.position)
        val range = Range(lspPos, lspPos)
        val detail = "procedure (${node.params.size} params)"
        return DocumentSymbol(node.name, SymbolKind.Function, range, range).apply {
            this.detail = detail
        }
    }

    private fun variableSymbol(name: String, astPosition: dev.marko.lsp.logo.parser.Position): DocumentSymbol {
        val lspPos = toLspPosition(astPosition)
        val range = Range(lspPos, lspPos)
        return DocumentSymbol(name, SymbolKind.Variable, range, range).apply {
            detail = "variable"
        }
    }

    // Position helpers

    private fun toLspPosition(pos: dev.marko.lsp.logo.parser.Position): Position =
        Position(
            (pos.line - 1).coerceAtLeast(0),
            (pos.column - 1).coerceAtLeast(0)
        )
}
