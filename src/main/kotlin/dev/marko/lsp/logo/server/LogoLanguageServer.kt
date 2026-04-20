package dev.marko.lsp.logo.server

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

/**
 * LOGO Language Server — the main entry point for the LSP protocol.
 *
 * Implements the [LanguageServer] and [LanguageClientAware] interfaces from
 * LSP4J. On [initialize] the server advertises its capabilities to the client.
 *
 * ### Advertised capabilities
 * | Capability                | Value                         |
 * |---------------------------|-------------------------------|
 * | `textDocumentSync`        | [TextDocumentSyncKind.Full]   |
 * | `declarationProvider`     | `true` (stub, for Phase 8)   |
 * | `semanticTokensProvider`  | stub legend (for Phase 7)    |
 */
class LogoLanguageServer : LanguageServer, LanguageClientAware {

    private val textDocumentService = LogoTextDocumentService()
    private val workspaceService = LogoWorkspaceService()

    /** Connected client reference, set via [connect]. */
    private var client: LanguageClient? = null

    // LanguageClientAware

    override fun connect(client: LanguageClient) {
        this.client = client
        textDocumentService.connect(client)
    }

    // LanguageServer

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities().apply {

            // Full document sync — the client sends the entire document on change
            setTextDocumentSync(TextDocumentSyncKind.Full)

            // Declaration provider, stub for Phase 8
            setDeclarationProvider(true)

            // Semantic tokens provider, Phase 7
            semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
                legend = SemanticTokensLegend(
                    listOf("keyword", "function", "variable", "number", "string", "comment"),
                    emptyList()
                )
                setFull(true)
            }
        }

        val serverInfo = ServerInfo("LOGO Language Server", "1.0.0")
        return CompletableFuture.completedFuture(InitializeResult(capabilities, serverInfo))
    }

    override fun initialized(params: InitializedParams) {
        System.err.println("LOGO Language Server initialized.")
    }

    override fun shutdown(): CompletableFuture<Any> {
        System.err.println("LOGO Language Server shutting down.")
        return CompletableFuture.completedFuture(null)
    }

    override fun exit() {
        System.err.println("LOGO Language Server exiting.")
        System.exit(0)
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService
}
