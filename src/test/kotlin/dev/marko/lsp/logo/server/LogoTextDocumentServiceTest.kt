package dev.marko.lsp.logo.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [LogoTextDocumentService].
 *
 * Verifies document lifecycle (open/change/close) and that the analysis
 * pipeline produces correct diagnostics through a capturing [LanguageClient].
 */
class LogoTextDocumentServiceTest {

    private class CapturingClient : LanguageClient {
        val published = mutableListOf<PublishDiagnosticsParams>()

        override fun telemetryEvent(obj: Any?) {}
        override fun publishDiagnostics(params: PublishDiagnosticsParams) {
            published.add(params)
        }
        override fun showMessage(params: MessageParams?) {}
        override fun showMessageRequest(params: org.eclipse.lsp4j.ShowMessageRequestParams?): java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.MessageActionItem> =
            java.util.concurrent.CompletableFuture.completedFuture(org.eclipse.lsp4j.MessageActionItem())
        override fun logMessage(params: MessageParams?) {}
    }

    private lateinit var service: LogoTextDocumentService
    private lateinit var client: CapturingClient

    @BeforeEach
    fun setUp() {
        service = LogoTextDocumentService()
        client = CapturingClient()
        service.connect(client)
    }

    // helper

    private fun openDoc(uri: String, text: String) {
        service.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, text)))
    }

    private fun changeDoc(uri: String, version: Int, text: String) {
        service.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, version),
                listOf(TextDocumentContentChangeEvent(text))
            )
        )
    }

    private fun closeDoc(uri: String) {
        service.didClose(DidCloseTextDocumentParams(TextDocumentIdentifier(uri)))
    }

    // tests

    @Test
    fun `didOpen stores document text`() {
        openDoc("file:///a.logo", "FD 100")
        assertEquals("FD 100", service.getDocumentText("file:///a.logo"))
    }

    @Test
    fun `didChange updates stored text`() {
        openDoc("file:///a.logo", "FD 100")
        changeDoc("file:///a.logo", 2, "RT 90")
        assertEquals("RT 90", service.getDocumentText("file:///a.logo"))
    }

    @Test
    fun `didClose removes document`() {
        openDoc("file:///a.logo", "FD 100")
        closeDoc("file:///a.logo")
        assertNull(service.getDocumentText("file:///a.logo"))
    }

    @Test
    fun `valid program publishes zero diagnostics`() {
        openDoc("file:///valid.logo", "FD 100\nRT 90")

        val lastPublish = client.published.last()
        assertEquals("file:///valid.logo", lastPublish.uri)
        assertTrue(lastPublish.diagnostics.isEmpty(), "Expected no diagnostics for valid code")
    }

    @Test
    fun `undefined variable publishes a diagnostic`() {
        openDoc("file:///err.logo", "FD :x")

        val lastPublish = client.published.last()
        assertEquals(1, lastPublish.diagnostics.size)
        assertTrue(lastPublish.diagnostics[0].message.contains("Undefined variable"))
        assertEquals(DiagnosticSeverity.Error, lastPublish.diagnostics[0].severity)
    }

    @Test
    fun `parse error publishes a diagnostic`() {
        // Unmatched bracket — should produce a parse error
        openDoc("file:///parse.logo", "REPEAT 4 [")

        val lastPublish = client.published.last()
        assertTrue(lastPublish.diagnostics.isNotEmpty(), "Expected parse error diagnostics")
        assertEquals(DiagnosticSeverity.Error, lastPublish.diagnostics[0].severity)
    }

    @Test
    fun `fixing code clears diagnostics`() {
        openDoc("file:///fix.logo", "FD :x")
        assertTrue(client.published.last().diagnostics.isNotEmpty())

        // Fix the code
        changeDoc("file:///fix.logo", 2, "FD 100")
        assertTrue(client.published.last().diagnostics.isEmpty(), "Expected cleared diagnostics after fix")
    }

    @Test
    fun `closing document clears diagnostics`() {
        openDoc("file:///close.logo", "FD :x")
        closeDoc("file:///close.logo")

        val lastPublish = client.published.last()
        assertEquals("file:///close.logo", lastPublish.uri)
        assertTrue(lastPublish.diagnostics.isEmpty(), "Expected cleared diagnostics after close")
    }
}
