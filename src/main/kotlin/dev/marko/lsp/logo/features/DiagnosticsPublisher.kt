package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.analysis.SemanticError
import dev.marko.lsp.logo.parser.ParseException
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient

/**
 * Converts parse and semantic errors into LSP [Diagnostic] objects and
 * publishes them to the connected [LanguageClient].
 *
 * ### Position mapping
 * Both [ParseException] and [SemanticError] use **1-based** line/column
 * numbers, whereas LSP uses **0-based** positions. This class performs
 * the conversion automatically.
 */
class DiagnosticsPublisher(private val client: LanguageClient) {

    /**
     * Publishes diagnostics for the given document URI.
     *
     * @param uri           The document URI (as received from the editor).
     * @param parseErrors   Errors collected during parsing.
     * @param semanticErrors Errors collected during semantic analysis.
     */
    fun publish(
        uri: String,
        parseErrors: List<ParseException>,
        semanticErrors: List<SemanticError>
    ) {
        val diagnostics = mutableListOf<Diagnostic>()

        for (err in parseErrors) {
            diagnostics += Diagnostic(
                toRange(err.line, err.column),
                err.message ?: "Parse error",
                DiagnosticSeverity.Error,
                "logo-parser"
            )
        }

        for (err in semanticErrors) {
            diagnostics += Diagnostic(
                toRange(err.line, err.column),
                err.message,
                DiagnosticSeverity.Error,
                "logo-semantic"
            )
        }

        client.publishDiagnostics(
            PublishDiagnosticsParams(uri, diagnostics)
        )
    }

    /**
     * Converts 1-based line/column to a 0-based LSP [Range].
     *
     * The range covers a single character at the specified position.
     * This is reasonable for error markers; more precise spans could
     * be added later when the AST carries end-positions.
     */
    private fun toRange(line: Int, column: Int): Range {
        val zeroLine = (line - 1).coerceAtLeast(0)
        val zeroCol  = (column - 1).coerceAtLeast(0)
        return Range(
            Position(zeroLine, zeroCol),
            Position(zeroLine, zeroCol + 1)
        )
    }
}
