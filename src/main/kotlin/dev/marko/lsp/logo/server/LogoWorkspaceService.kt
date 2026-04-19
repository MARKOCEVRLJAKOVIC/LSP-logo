package dev.marko.lsp.logo.server

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.WorkspaceService

/**
 * Stub implementation of [WorkspaceService].
 *
 * LOGO does not currently support workspace-level features, so every
 * notification is simply ignored.
 */
class LogoWorkspaceService : WorkspaceService {

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        // no-op
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        // no-op
    }
}
