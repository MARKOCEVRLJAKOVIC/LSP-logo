package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.analysis.SemanticError
import dev.marko.lsp.logo.parser.ParseException
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [DiagnosticsPublisher].
 *
 * Uses a simple [CapturingClient] to verify that diagnostics are published
 * with correct 0-based positions, appropriate severity, and source labels.
 */
class DiagnosticsPublisherTest {

    /** Spy client that records every [publishDiagnostics] call. */
    private class CapturingClient : LanguageClient {
        val published = mutableListOf<PublishDiagnosticsParams>()

        override fun telemetryEvent(obj: Any?) {}
        override fun publishDiagnostics(params: PublishDiagnosticsParams) {
            published.add(params)
        }
        override fun showMessage(params: org.eclipse.lsp4j.MessageParams?) {}
        override fun showMessageRequest(params: org.eclipse.lsp4j.ShowMessageRequestParams?): java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.MessageActionItem> =
            java.util.concurrent.CompletableFuture.completedFuture(org.eclipse.lsp4j.MessageActionItem())
        override fun logMessage(params: org.eclipse.lsp4j.MessageParams?) {}
    }

    private lateinit var client: CapturingClient
    private lateinit var publisher: DiagnosticsPublisher

    @BeforeEach
    fun setUp() {
        client = CapturingClient()
        publisher = DiagnosticsPublisher(client)
    }

    @Test
    fun `empty errors publishes empty diagnostics list`() {
        publisher.publish("file:///test.logo", emptyList(), emptyList())

        assertEquals(1, client.published.size)
        val params = client.published[0]
        assertEquals("file:///test.logo", params.uri)
        assertTrue(params.diagnostics.isEmpty())
    }

    @Test
    fun `parse error is converted with 0-based positions`() {
        val parseError = ParseException("Unexpected token", 3, 7)
        publisher.publish("file:///test.logo", listOf(parseError), emptyList())

        assertEquals(1, client.published.size)
        val diag = client.published[0].diagnostics.single()

        // 1-based (3,7) → 0-based (2,6)
        assertEquals(2, diag.range.start.line)
        assertEquals(6, diag.range.start.character)
        assertEquals(2, diag.range.end.line)
        assertEquals(7, diag.range.end.character)
        assertEquals(DiagnosticSeverity.Error, diag.severity)
        assertEquals("logo-parser", diag.source)
        assertEquals("Unexpected token", diag.message)
    }

    @Test
    fun `semantic error is converted with 0-based positions`() {
        val semError = SemanticError("Undefined variable: x", 5, 10)
        publisher.publish("file:///test.logo", emptyList(), listOf(semError))

        val diag = client.published[0].diagnostics.single()

        // 1-based (5,10) → 0-based (4,9)
        assertEquals(4, diag.range.start.line)
        assertEquals(9, diag.range.start.character)
        assertEquals(DiagnosticSeverity.Error, diag.severity)
        assertEquals("logo-semantic", diag.source)
    }

    @Test
    fun `both parse and semantic errors are combined`() {
        val parseErrors = listOf(
            ParseException("Error A", 1, 1),
            ParseException("Error B", 2, 3)
        )
        val semanticErrors = listOf(
            SemanticError("Error C", 4, 5)
        )

        publisher.publish("file:///test.logo", parseErrors, semanticErrors)

        val diagnostics = client.published[0].diagnostics
        assertEquals(3, diagnostics.size)
        assertEquals("Error A", diagnostics[0].message)
        assertEquals("Error B", diagnostics[1].message)
        assertEquals("Error C", diagnostics[2].message)
    }

    @Test
    fun `position at line 1 column 1 maps to 0-based 0-0`() {
        val err = SemanticError("Error at origin", 1, 1)
        publisher.publish("file:///test.logo", emptyList(), listOf(err))

        val diag = client.published[0].diagnostics.single()
        assertEquals(0, diag.range.start.line)
        assertEquals(0, diag.range.start.character)
    }
}
