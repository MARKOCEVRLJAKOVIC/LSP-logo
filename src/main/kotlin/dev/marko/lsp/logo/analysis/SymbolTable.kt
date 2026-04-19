package dev.marko.lsp.logo.analysis

import dev.marko.lsp.logo.parser.Position

/**
 * Symbol representing a user-defined procedure.
 *
 * @property name       Uppercased procedure name (LOGO is case-insensitive).
 * @property paramCount Number of formal parameters.
 * @property position   Source location of the `TO` keyword that defines it.
 */
data class ProcedureSymbol(
    val name: String,
    val paramCount: Int,
    val position: Position
)

/**
 * Symbol representing a variable visible in the current scope.
 *
 * @property name     Uppercased variable name.
 * @property position Source location where the variable was first introduced.
 */
data class VariableSymbol(
    val name: String,
    val position: Position
)

/**
 * A hierarchical symbol table for LOGO programs.
 *
 * **Procedures** are registered in a single, flat (global) map — LOGO does
 * not support procedure overloading or nested procedure namespaces.
 *
 * **Variables** are scoped: a new scope is pushed when entering a procedure
 * body (or any other block that introduces names, like `FOR`), and popped
 * when leaving it.  Variable lookup walks the scope stack from innermost
 * to outermost, implementing lexical scoping.
 *
 * All names are normalized to **uppercase** before storage and lookup
 * because LOGO is case-insensitive.
 */
class SymbolTable {

    // Procedure registry (global)

    private val procedures: MutableMap<String, ProcedureSymbol> = mutableMapOf()

    /**
     * Registers a user-defined procedure.
     * The [name] is uppercased internally.
     */
    fun defineProc(name: String, paramCount: Int, position: Position) {
        procedures[name.uppercase()] = ProcedureSymbol(name.uppercase(), paramCount, position)
    }

    /**
     * Looks up a procedure by [name] (case-insensitive).
     * Returns `null` if no procedure with that name has been defined.
     */
    fun lookupProc(name: String): ProcedureSymbol? = procedures[name.uppercase()]

    // Variable scopes

    /**
     * Stack of variable scopes.  Index 0 is the global scope, and each
     * subsequent entry is a nested scope (procedure body, FOR block, etc.).
     */
    private val scopes: MutableList<MutableMap<String, VariableSymbol>> = mutableListOf(mutableMapOf())

    /**
     * Pushes a new, empty scope onto the scope stack.
     * Call this when entering a procedure body or a `FOR` loop.
     */
    fun enterScope() {
        scopes.add(mutableMapOf())
    }

    /**
     * Pops the innermost scope from the scope stack.
     * Call this when leaving a procedure body or a `FOR` loop.
     */
    fun exitScope() {
        if (scopes.size > 1) {
            scopes.removeAt(scopes.lastIndex)
        }
    }

    /**
     * Defines a variable in the **current** (innermost) scope.
     * The [name] is uppercased internally.
     */
    fun defineVar(name: String, position: Position) {
        scopes.last()[name.uppercase()] = VariableSymbol(name.uppercase(), position)
    }

    /**
     * Looks up a variable by [name] (case-insensitive), walking from
     * the innermost scope outward.
     *
     * Returns `null` if the variable is not defined in any visible scope.
     */
    fun lookupVar(name: String): VariableSymbol? {
        val upper = name.uppercase()
        for (i in scopes.indices.reversed()) {
            scopes[i][upper]?.let { return it }
        }
        return null
    }
}
