package dev.marko.lsp.logo.lexer

/**
 * All token types recognized by the LOGO lexer.
 *
 * Token classification follows a **two-tier** model:
 *  - **True keywords** affect parsing structure (e.g. [TO], [END], [REPEAT]).
 *    Each gets its own enum entry so the parser can match on them directly.
 *  - **Built-in procedures** (e.g. forward, setcolor, arc) are all represented
 *    by a single [BUILTIN] entry — the parser treats them uniformly as
 *    procedure calls and distinguishes them by lexeme when needed.
 *
 * Additional notes:
 *  - [STRING] covers LOGO string literals that start with a single `"`.
 *  - [LESS], [GREATER], [EQUAL] support conditional expressions like `:x < 10`.
 *  - [NEWLINE] is preserved so the parser can use it as a statement separator.
 */
enum class TokenType {

    //  True keywords (affect parsing structure) 
    TO,
    END,
    MAKE,
    REPEAT,
    FOR,
    IF,
    IFELSE,
    DO_WHILE,
    STOP,
    OUTPUT,

    //  Built-in procedures (uniform for the parser) 
    BUILTIN,

    //  Literals 
    NUMBER,
    STRING,

    //  Identifiers & Variables 
    IDENT,
    VARIABLE,       // e.g. :x, :size

    //  Symbols 
    LPAREN,         // (
    RPAREN,         // )
    LBRACKET,       // [
    RBRACKET,       // ]

    //  Arithmetic Operators 
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /

    //  Comparison Operators 
    LESS,           // <
    GREATER,        // >
    EQUAL,          // =

    //  Comments 
    COMMENT,        // ; to end of line

    //  Special 
    NEWLINE,
    EOF,
    UNKNOWN
}
