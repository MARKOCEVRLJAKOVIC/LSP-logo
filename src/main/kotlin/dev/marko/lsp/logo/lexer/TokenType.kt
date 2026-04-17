package dev.marko.lsp.logo.lexer

/**
 * All token types recognized by the LOGO lexer.
 *
 * Grouped by category for clarity:
 *  - Keywords map 1:1 to LOGO reserved words (matched case-insensitively).
 *  - LBRACKET/RBRACKET are included because LOGO uses [ ] for repeat bodies.
 *  - Arithmetic operators are included to support expressions like `FORWARD :x + 10`.
 *  - NEWLINE is preserved as a token so the parser can use it as a statement separator.
 */
enum class TokenType {

    // Keywords
    TO,
    END,
    MAKE,
    FORWARD,
    RIGHT,
    LEFT,
    REPEAT,

    // Literals
    NUMBER,

    // Identifiers & Variables
    IDENT,
    VARIABLE, // e.g. :x, :size

    // Symbols
    LPAREN,         // (
    RPAREN,         // )
    LBRACKET,       // [
    RBRACKET,       // ]

    // Arithmetic Operators
    PLUS,   // +
    MINUS,  // -
    STAR,   // *
    SLASH,  // /

    // Special
    NEWLINE,
    EOF,
    UNKNOWN
}
