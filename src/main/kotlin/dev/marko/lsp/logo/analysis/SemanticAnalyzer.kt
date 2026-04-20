package dev.marko.lsp.logo.analysis

import dev.marko.lsp.logo.lexer.Lexer
import dev.marko.lsp.logo.parser.*

/**
 * Single-pass semantic analyzer for LOGO AST trees.
 *
 * The analyzer performs **two phases** over the AST:
 *
 * 1. **Registration pass** — walks top-level statements to register all
 *    `TO … END` procedure definitions in the [SymbolTable] so that
 *    forward-references (calling a procedure before its textual definition)
 *    are supported.
 *
 * 2. **Validation pass** — recursively visits every node and checks:
 *    - Procedure calls reference either a built-in or a user-defined proc.
 *    - Argument counts match the formal parameter count for user procedures.
 *    - Every variable reference (`:name`) is in scope (parameter, `MAKE`, or
 *      `FOR` loop variable).
 *
 * All detected problems are accumulated in [errors]; the analyzer never
 * throws on the first failure.
 */
class SemanticAnalyzer {

    val symbolTable = SymbolTable()
    val errors: MutableList<SemanticError> = mutableListOf()

    /**
     * Set of uppercased built-in procedure names.
     * Reuses the canonical list from the [Lexer] companion object.
     */
    private val builtinNames: Set<String> = Lexer.BUILTINS

    /**
     * Set of uppercased keyword names that are parsed as their own AST nodes
     * (e.g. REPEAT, FOR, MAKE, IF, IFELSE, etc.).
     * These should also be treated as "known" when encountered as identifiers
     * in certain positions.
     */
    private val keywordNames: Set<String> = Lexer.KEYWORDS.keys

    // Public API

    /**
     * Analyzes the given [program] and returns the list of semantic errors.
     *
     * The [SymbolTable] is populated as a side-effect and can be inspected
     * afterward for tooling features like go-to-definition.
     */
    fun analyze(program: ProgramNode): List<SemanticError> {
        // Phase 1: register all procedure definitions (enables forward refs)
        registerAllProcedures(program)

        // Phase 2: validate the full tree
        for (stmt in program.statements) {
            visitNode(stmt)
        }

        return errors.toList()
    }

    // Phase-1 helpers

    /**
     * Recursively walks the AST and registers every [ProcedureDefNode]
     * in the [SymbolTable], no matter how deeply nested it is.
     */
    private fun registerAllProcedures(node: Node) {
        when (node) {
            is ProgramNode       -> node.statements.forEach { registerAllProcedures(it) }
            is ProcedureDefNode  -> {
                symbolTable.defineProc(node.name, node.params.size, node.position)
                node.body.forEach { registerAllProcedures(it) }
            }
            is RepeatNode        -> node.body.forEach { registerAllProcedures(it) }
            is ForNode           -> node.body.forEach { registerAllProcedures(it) }
            is IfNode            -> node.body.forEach { registerAllProcedures(it) }
            is IfElseNode        -> {
                node.thenBody.forEach { registerAllProcedures(it) }
                node.elseBody.forEach { registerAllProcedures(it) }
            }
            is DoWhileNode       -> node.body.forEach { registerAllProcedures(it) }
            is BlockNode         -> node.statements.forEach { registerAllProcedures(it) }
            else                 -> { /* leaf nodes — nothing to traverse */ }
        }
    }

    // Visitor dispatch

    private fun visitNode(node: Node) {
        when (node) {
            is ProgramNode       -> node.statements.forEach { visitNode(it) }
            is ProcedureDefNode  -> visitProcedureDef(node)
            is ProcedureCallNode -> visitProcedureCall(node)
            is RepeatNode        -> visitRepeat(node)
            is ForNode           -> visitFor(node)
            is MakeNode          -> visitMake(node)
            is IfNode            -> visitIf(node)
            is IfElseNode        -> visitIfElse(node)
            is DoWhileNode       -> visitDoWhile(node)
            is BinaryExprNode    -> visitBinaryExpr(node)
            is OutputNode        -> visitOutput(node)
            is BlockNode         -> node.statements.forEach { visitNode(it) }
            is VariableNode      -> visitVariable(node)
            is NumberNode        -> { /* nothing to check */ }
            is StringNode        -> { /* nothing to check */ }
            is StopNode          -> { /* nothing to check */ }
        }
    }

    // Node visitors

    /**
     * Enters a new scope, defines the formal parameters as variables,
     * visits the body, then exits the scope.
     */
    private fun visitProcedureDef(node: ProcedureDefNode) {
        symbolTable.enterScope()

        // Register each formal parameter as a variable in this scope
        for (param in node.params) {
            symbolTable.defineVar(param, node.position)
        }

        node.body.forEach { visitNode(it) }

        symbolTable.exitScope()
    }

    /**
     * Validates a procedure call:
     * - Built-in names are always accepted.
     * - User-defined names must exist in the symbol table, and the argument
     *   count must match the declared parameter count.
     */
    private fun visitProcedureCall(node: ProcedureCallNode) {
        val upperName = node.name.uppercase()

        // Visit arguments first (they may contain variable refs, sub-calls, etc.)
        node.args.forEach { visitNode(it) }

        // Built-in procedures are always valid
        if (upperName in builtinNames || upperName in keywordNames) return

        val procSymbol = symbolTable.lookupProc(upperName)
        if (procSymbol == null) {
            errors += SemanticError(
                "Undefined procedure: ${node.name}",
                node.position.line,
                node.position.column,
                node.name.length
            )
        } else if (node.args.size != procSymbol.paramCount) {
            errors += SemanticError(
                "Procedure '${node.name}' expects ${procSymbol.paramCount} argument(s) but got ${node.args.size}",
                node.position.line,
                node.position.column,
                node.name.length
            )
        }
    }

    private fun visitRepeat(node: RepeatNode) {
        visitNode(node.count)
        node.body.forEach { visitNode(it) }
    }

    /**
     * FOR loop: the loop variable is visible inside the body.
     */
    private fun visitFor(node: ForNode) {
        visitNode(node.start)
        visitNode(node.end)
        visitNode(node.step)

        symbolTable.enterScope()
        symbolTable.defineVar(node.variable, node.position)
        node.body.forEach { visitNode(it) }
        symbolTable.exitScope()
    }

    /**
     * MAKE defines (or redefines) a variable in the current scope.
     */
    private fun visitMake(node: MakeNode) {
        visitNode(node.value)
        symbolTable.defineVar(node.varName, node.position)
    }

    private fun visitIf(node: IfNode) {
        visitNode(node.condition)
        node.body.forEach { visitNode(it) }
    }

    private fun visitIfElse(node: IfElseNode) {
        visitNode(node.condition)
        node.thenBody.forEach { visitNode(it) }
        node.elseBody.forEach { visitNode(it) }
    }

    private fun visitDoWhile(node: DoWhileNode) {
        node.body.forEach { visitNode(it) }
        visitNode(node.condition)
    }

    private fun visitBinaryExpr(node: BinaryExprNode) {
        visitNode(node.left)
        visitNode(node.right)
    }

    private fun visitOutput(node: OutputNode) {
        visitNode(node.value)
    }

    /**
     * Checks that a referenced variable is defined in some visible scope.
     */
    private fun visitVariable(node: VariableNode) {
        if (symbolTable.lookupVar(node.name) == null) {
            errors += SemanticError(
                "Undefined variable: ${node.name}",
                node.position.line,
                node.position.column,
                node.name.length + 1
            )
        }
    }
}
