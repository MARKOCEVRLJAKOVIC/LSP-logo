package dev.marko.lsp.logo

import dev.marko.lsp.logo.server.LogoLanguageServer
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageClient

/**
 * Entry point for the LOGO Language Server.
 *
 * Starts a JSON-RPC connection over **stdin / stdout**.
 * All diagnostic or log output is written to **stderr** so that
 * the stdio transport stays clean.
 */
fun main() {
    // Redirect any accidental stdout usage to stderr.
    // LSP communication MUST be the only thing on stdout.
    val originalOut = System.out
    System.setOut(System.err)

    val server = LogoLanguageServer()

    val launcher = Launcher.createLauncher(
        server,
        LanguageClient::class.java,
        System.`in`,
        originalOut   // LSP JSON-RPC goes to the *original* stdout
    )

    // Wire the client proxy into the server so it can push diagnostics
    server.connect(launcher.remoteProxy)

    System.err.println("LOGO Language Server starting on stdio…")
    launcher.startListening().get()
}
