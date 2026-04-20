package dev.marko.lsp.logo.server

import dev.marko.lsp.logo.analysis.SemanticAnalyzer
import dev.marko.lsp.logo.features.DiagnosticsPublisher
import dev.marko.lsp.logo.features.SemanticTokensProvider
import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.parser.ParseException
import dev.marko.lsp.logo.parser.Parser
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles all text-document-level LSP notifications and requests for LOGO.
 *
 * Maintains an **in-memory map** of open documents (`URI → source text`).
 * Every time a document is opened or changed the full analysis pipeline is
 * executed: **Lexer → Parser → SemanticAnalyzer**, and the resulting
 * diagnostics are pushed to the client.
 *
 * We use [TextDocumentSyncKind.Full], so each `didChange` notification
 * carries the complete new document text.
 */
class LogoTextDocumentService : TextDocumentService {

    /** Open documents: URI → latest full source text. */
    private val documents = ConcurrentHashMap<String, String>()

    /** Set after the server connects to the client. */
    private var diagnosticsPublisher: DiagnosticsPublisher? = null

    /**
     * Called by [LogoLanguageServer] once the client connection is known.
     */
    fun connect(client: LanguageClient) {
        diagnosticsPublisher = DiagnosticsPublisher(client)
    }

    // Document synchronisation

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val text = params.textDocument.text
        documents[uri] = text
        analyzeAndPublish(uri, text)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        // Full sync — the first (and only) content change carries the whole text
        val text = params.contentChanges.firstOrNull()?.text ?: return
        documents[uri] = text
        analyzeAndPublish(uri, text)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = params.textDocument.uri
        documents.remove(uri)
        // Clear diagnostics for the closed document
        diagnosticsPublisher?.publish(uri, emptyList(), emptyList())
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // no-op — we already react to didChange
    }

    // Analysis pipeline

    /**
     * Runs the full **Lexer → Parser → SemanticAnalyzer** pipeline on [text]
     * and publishes diagnostics for [uri].
     *
     * If the parser encounters fatal errors that prevent building a valid AST
     * the parse errors are still published.
     */
    private fun analyzeAndPublish(uri: String, text: String) {
        val parseErrors = mutableListOf<ParseException>()
        val semanticErrors: List<dev.marko.lsp.logo.analysis.SemanticError>

        try {
            // 1. Lex
            val tokens = Lexer(text).tokenize()

            // 2. Parse
            val parser = Parser(tokens)
            val program = parser.parseProgram()
            parseErrors.addAll(parser.errors)

            // 3. Semantic analysis
            val analyzer = SemanticAnalyzer()
            semanticErrors = analyzer.analyze(program)
        } catch (e: ParseException) {
            // Fatal parse error – still publish what we have
            parseErrors.add(e)
            diagnosticsPublisher?.publish(uri, parseErrors, emptyList())
            return
        } catch (e: Exception) {
            // Unexpected error – log to stderr, do not crash the server
            System.err.println("Analysis error for $uri: ${e.message}")
            return
        }

        diagnosticsPublisher?.publish(uri, parseErrors, semanticErrors)
    }

    // Stub implementations for unimplemented features

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        return CompletableFuture.completedFuture(null)
    }

    override fun declaration(params: DeclarationParams): CompletableFuture<Either<List<out Location>, List<out LocationLink>>> {
        return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<out Location>, List<out LocationLink>>> {
        return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
    }

    override fun references(params: ReferenceParams): CompletableFuture<List<out Location>> {
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
        return CompletableFuture.completedFuture(emptyList())
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<out TextEdit>> {
        return CompletableFuture.completedFuture(emptyList())
    }

    // Semantic tokens

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val text = documents[params.textDocument.uri]
            ?: return CompletableFuture.completedFuture(SemanticTokens(emptyList()))
        val tokens = SemanticTokensProvider().provide(text)
        return CompletableFuture.completedFuture(tokens)
    }

    // Accessors for testing

    /** Returns the current source text for a document, or null if not open. */
    fun getDocumentText(uri: String): String? = documents[uri]
}
