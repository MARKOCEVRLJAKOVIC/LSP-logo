package dev.marko.lsp.logo.parser

/**
 * Represents a source position in a LOGO program, identified by line and column number.
 */
data class Position(val line: Int, val column: Int)