package dev.marko.lsp.logo.server

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [LogoLanguageServer].
 *
 * Verifies that the server returns the correct capabilities on initialize.
 */
class LogoLanguageServerTest {

    @Test
    fun `initialize returns Full text document sync`() {
        val server = LogoLanguageServer()
        val result = server.initialize(InitializeParams()).get()

        val syncKind = result.capabilities.textDocumentSync
        assertNotNull(syncKind)
        assertEquals(TextDocumentSyncKind.Full, syncKind.left)
    }

    @Test
    fun `initialize advertises declaration provider`() {
        val server = LogoLanguageServer()
        val result = server.initialize(InitializeParams()).get()

        val declProvider = result.capabilities.declarationProvider
        assertNotNull(declProvider, "declarationProvider should be set")
    }

    @Test
    fun `initialize advertises semantic tokens provider`() {
        val server = LogoLanguageServer()
        val result = server.initialize(InitializeParams()).get()

        val semanticTokens = result.capabilities.semanticTokensProvider
        assertNotNull(semanticTokens, "semanticTokensProvider should be set")
    }

    @Test
    fun `server info is present`() {
        val server = LogoLanguageServer()
        val result = server.initialize(InitializeParams()).get()

        assertNotNull(result.serverInfo)
        assertEquals("LOGO Language Server", result.serverInfo.name)
    }

    @Test
    fun `shutdown returns null successfully`() {
        val server = LogoLanguageServer()
        val result = server.shutdown().get()
        assertNull(result)
    }
}
