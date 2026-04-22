# LOGO Language Server

A Language Server for the [LOGO](https://en.wikipedia.org/wiki/Logo_(programming_language)) programming language, implemented in Kotlin. It communicates with any LSP-capable editor over the Language Server Protocol (JSON-RPC over stdio) and provides real-time diagnostics, navigation, hover information, document symbols, and semantic syntax highlighting.

---

## Features

| LSP Feature | Status | 
|---|---|
| **Syntax Highlighting** | Semantic tokens for keywords, built-ins, variables, numbers, strings, and comments |
| **Go-to-Declaration** | Jump to procedure and variable definitions |
| **Hover** | Shows parameter count & definition location for procedures; definition location for variables; labels built-ins |
| **Diagnostics** | Real-time parse errors and semantic errors (undefined procedures/variables, wrong argument counts) |
| **Document Symbols** | Outline view listing all procedures and top-level variables |

## Not yet implemented

- No refactoring support (e.g. rename, change signature)
- No code completion
- No formatting support

---

## Building

### Prerequisites

- **JDK 21** (set `JAVA_HOME` accordingly)
- **Gradle 8.x** (or use the included wrapper)

### Build an executable fat JAR

```bash
./gradlew shadowJar
```

The output JAR will be at:

```
build/libs/lsp_logo-1.0-SNAPSHOT-all.jar
```

### Run tests

```bash
./gradlew test
```

---

## Running the Server

The server communicates over **stdin/stdout** (the standard LSP stdio transport). All internal logging goes to **stderr** so the JSON-RPC channel stays clean.

```bash
java -jar build/libs/lsp_logo-1.0-SNAPSHOT-all.jar
```

You should see on stderr:

```
LOGO Language Server starting on stdio…
```

---

## Connecting to an LSP Client

### LSP4IJ (IntelliJ-based IDEs)

1. Open **Settings → Languages & Frameworks → Language Servers**.
2. **Register the server command**
   Go to **Server → Command** and enter:
   ```
   java -jar C:\Users\PC\IdeaProjects\lsp_logo\build\libs\lsp_logo-1.0-SNAPSHOT-all.jar
   ```
3. **Map the file extension**
   Go to **Mappings → Filename Patterns** and add:
   ```
   *.logo
   ```
4. After applying, any `.logo` file opened in IntelliJ will be serviced by the LOGO Language Server.

5. Open any `.logo` file — the server will start automatically.

### VS Code (via `vscode-languageclient`)

Configure a generic LSP client extension (e.g. *Language Support for Java*) to launch the JAR for files matching `**/*.logo`, using the stdio transport.

---

## Architecture & Project Layout

```
src/
├── main/kotlin/dev/marko/lsp/logo/
│   ├── Main.kt                        # Entry point — wires stdin/stdout transport
│   ├── lexer/
│   │   ├── Lexer.kt                   # Hand-written character scanner
│   │   ├── Token.kt                   # Token data class
│   │   └── TokenType.kt               # All token categories
│   ├── parser/
│   │   ├── Parser.kt                  # Recursive-descent parser
│   │   ├── Node.kt                    # Sealed AST node hierarchy
│   │   ├── Position.kt                # 1-based source position
│   │   └── ParseException.kt          # Recoverable parse error
│   ├── analysis/
│   │   ├── SemanticAnalyzer.kt        # Two-phase AST walker
│   │   ├── SymbolTable.kt             # Scoped proc & variable registry
│   │   └── SemanticError.kt           # Semantic error data class
│   ├── features/
│   │   ├── CursorResolver.kt          # Maps cursor position → AST symbol
│   │   ├── DiagnosticsPublisher.kt    # Converts errors → LSP Diagnostics
│   │   ├── DocumentSymbolProvider.kt  # Produces outline symbols
│   │   ├── HoverProvider.kt           # Markdown hover content
│   │   └── SemanticTokensProvider.kt  # Delta-encoded token stream
│   └── server/
│       ├── LogoLanguageServer.kt      # Capabilities advertisement & lifecycle
│       ├── LogoTextDocumentService.kt # Per-document analysis pipeline
│       └── LogoWorkspaceService.kt    # Stub workspace handler
└── test/kotlin/dev/marko/lsp/logo/
    ├── lexer/LexerTest.kt
    ├── parser/ParserTest.kt
    ├── analysis/SemanticAnalyzerTest.kt
    └── features/
        ├── CursorResolverTest.kt
        ├── DiagnosticsPublisherTest.kt
        ├── DocumentSymbolProviderTest.kt
        ├── HoverProviderTest.kt
        └── SemanticTokensProviderTest.kt
```

### Key design decisions

**Lexer** - Two-tier identifier resolution: identifiers are first matched against a keyword map (structural keywords like `TO`, `END`, `REPEAT`), then against a built-in set (turtle commands like `FD`, `SETCOLOR`), and finally fall back to `IDENT`. This keeps the parser simple — it only needs to special-case true keywords.

**Parser** - Recursive descent with *error recovery*: on a parse failure the parser records the exception, skips tokens until the next newline, and continues. This means multiple errors in one document are all reported at once rather than stopping at the first.

**SemanticAnalyzer** - Two-phase design:
1. *Registration pass* - walks the entire AST first to register all procedure definitions, enabling forward references (calling a procedure before its textual definition).
2. *Validation pass* - checks undefined procedures, wrong argument counts, and undefined variables, with proper lexical scoping (`TO` bodies and `FOR` loops push/pop scopes).

**Position conventions** — The lexer and AST use **1-based** line/column. All LSP-facing code (diagnostics, hover, go-to-declaration, semantic tokens) converts to **0-based** at the boundary, keeping the conversion in one place.

---

## LOGO Language Support

The server covers the following language constructs:

- Procedure definitions: `TO name :param … END`
- Turtle movement: `FORWARD`/`FD`, `BACK`/`BK`, `RIGHT`/`RT`, `LEFT`/`LT`
- Pen & screen control: `PENUP`, `PENDOWN`, `CLEARSCREEN`, `HOME`, `SETCOLOR`, `SETWIDTH`, …
- Control flow: `REPEAT`, `FOR`, `IF`, `IFELSE`, `DO.WHILE`
- Variables: `MAKE "x 10`, `:x`
- Arithmetic & comparison: `+ - * / < > =`
- I/O: `PRINT`, `TYPE`, `READWORD`
- Comments: `; to end of line`
- String literals: `"word`