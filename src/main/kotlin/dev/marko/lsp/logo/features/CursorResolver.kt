package dev.marko.lsp.logo.features

import dev.marko.lsp.logo.parser.*

/**
 * Result of resolving a cursor position to a symbol in the AST.
 */
sealed class ResolvedSymbol {
    /** The cursor is on a procedure call name. */
    data class ResolvedProc(val name: String) : ResolvedSymbol()

    /** The cursor is on a variable reference name. */
    data class ResolvedVar(val name: String) : ResolvedSymbol()
}

/**
 * Resolves the symbol under the cursor at a given (0-based) LSP position
 * by walking the LOGO AST.
 *
 * AST positions are **1-based** (line and column), so this class converts
 * the incoming 0-based LSP coordinates to 1-based before comparison.
 *
 * A symbol "matches" if the cursor column falls within:
 *   `node.position.column <= col1 < node.position.column + name.length`
 * and `node.position.line == line1`.
 */
class CursorResolver {

    /**
     * Finds the symbol at the given 0-based [line]/[column] position.
     *
     * @param program The root AST node.
     * @param line    0-based line (LSP convention).
     * @param column  0-based column (LSP convention).
     * @return The resolved symbol, or `null` if no recognisable symbol is at that position.
     */
    fun resolve(program: ProgramNode, line: Int, column: Int): ResolvedSymbol? {
        // Convert 0-based LSP position to 1-based AST position
        val line1 = line + 1
        val col1 = column + 1
        return walk(program, line1, col1)
    }

    // Recursive AST walker

    private fun walk(node: Node, line: Int, col: Int): ResolvedSymbol? {
        return when (node) {
            is ProgramNode       -> node.statements.firstNotNullOfOrNull { walk(it, line, col) }
            is ProcedureDefNode  -> node.body.firstNotNullOfOrNull { walk(it, line, col) }
            is ProcedureCallNode -> matchProcCall(node, line, col)
                                    ?: node.args.firstNotNullOfOrNull { walk(it, line, col) }
            is VariableNode      -> matchVariable(node, line, col)
            is RepeatNode        -> walk(node.count, line, col)
                                    ?: node.body.firstNotNullOfOrNull { walk(it, line, col) }
            is ForNode           -> walk(node.start, line, col)
                                    ?: walk(node.end, line, col)
                                    ?: walk(node.step, line, col)
                                    ?: node.body.firstNotNullOfOrNull { walk(it, line, col) }
            is MakeNode          -> walk(node.value, line, col)
            is IfNode            -> walk(node.condition, line, col)
                                    ?: node.body.firstNotNullOfOrNull { walk(it, line, col) }
            is IfElseNode        -> walk(node.condition, line, col)
                                    ?: node.thenBody.firstNotNullOfOrNull { walk(it, line, col) }
                                    ?: node.elseBody.firstNotNullOfOrNull { walk(it, line, col) }
            is DoWhileNode       -> node.body.firstNotNullOfOrNull { walk(it, line, col) }
                                    ?: walk(node.condition, line, col)
            is BinaryExprNode    -> walk(node.left, line, col) ?: walk(node.right, line, col)
            is BlockNode         -> node.statements.firstNotNullOfOrNull { walk(it, line, col) }
            is OutputNode        -> walk(node.value, line, col)
            is NumberNode        -> null
            is StringNode        -> null
            is StopNode          -> null
        }
    }

    private fun matchProcCall(node: ProcedureCallNode, line: Int, col: Int): ResolvedSymbol? {
        if (node.position.line != line) return null
        val start = node.position.column
        val end = start + node.name.length
        return if (col in start until end) ResolvedSymbol.ResolvedProc(node.name) else null
    }

    private fun matchVariable(node: VariableNode, line: Int, col: Int): ResolvedSymbol? {
        if (node.position.line != line) return null
        // The VariableNode position points to the ':' character.
        // The full lexeme is ':' + name, so total length = 1 + name.length
        val start = node.position.column
        val end = start + 1 + node.name.length
        return if (col in start until end) ResolvedSymbol.ResolvedVar(node.name) else null
    }
}
