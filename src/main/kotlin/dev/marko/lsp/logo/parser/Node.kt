package dev.marko.lsp.logo.parser

/**
 * Base class for all AST nodes in a LOGO program.
 */
sealed class Node {
    abstract val position: Position
}

/** Represents a complete LOGO program consisting of a list of top-level statements. */
data class ProgramNode(
    val statements: List<Node>,
    override val position: Position
) : Node()

/** Represents a procedure definition (`TO ... END`). */
data class ProcedureDefNode(
    val name: String,
    val params: List<String>,
    val body: List<Node>,
    override val position: Position
) : Node()

/** Represents a procedure call (e.g., `SQUARE 100`). */
data class ProcedureCallNode(
    val name: String,
    val args: List<Node>,
    override val position: Position
) : Node()

/** Represents a variable reference (e.g., `:size`). */
data class VariableNode(
    val name: String,
    override val position: Position
) : Node()

/** Represents a numeric literal (e.g., `42`, `3.14`). */
data class NumberNode(
    val value: Double,
    override val position: Position
) : Node()

/** Represents a string literal (e.g., `"hello`). */
data class StringNode(
    val value: String,
    override val position: Position
) : Node()

/** Represents a `REPEAT` loop (e.g., `REPEAT 4 [FD 100 RT 90]`). */
data class RepeatNode(
    val count: Node,
    val body: List<Node>,
    override val position: Position
) : Node()

/** Represents a `FOR` loop (e.g., `FOR [i 1 10 1] [...]`). */
data class ForNode(
    val variable: String,
    val start: Node,
    val end: Node,
    val step: Node,
    val body: List<Node>,
    override val position: Position
) : Node()

/** Represents a `MAKE` variable assignment (e.g., `MAKE "x 10`). */
data class MakeNode(
    val varName: String,
    val value: Node,
    override val position: Position
) : Node()

/** Represents an `IF` conditional (e.g., `IF :x > 0 [FD 100]`). */
data class IfNode(
    val condition: Node,
    val body: List<Node>,
    override val position: Position
) : Node()

/** Represents an `IFELSE` conditional (e.g., `IFELSE :x > 0 [FD 100] [BK 100]`). */
data class IfElseNode(
    val condition: Node,
    val thenBody: List<Node>,
    val elseBody: List<Node>,
    override val position: Position
) : Node()

/** Represents a `DO.WHILE` loop that executes the body at least once. */
data class DoWhileNode(
    val body: List<Node>,
    val condition: Node,
    override val position: Position
) : Node()

/** Represents a binary expression (e.g., `:x + 1`, `:a > :b`). */
enum class BinaryOp { PLUS, MINUS, STAR, SLASH, LESS, GREATER, EQUAL }

data class BinaryExprNode(
    val op: BinaryOp,
    val left: Node,
    val right: Node,
    override val position: Position
) : Node()

/** Represents a bracketed block of statements (`[...]`). */
data class BlockNode(
    val statements: List<Node>,
    override val position: Position
) : Node()

/** Represents a `STOP` command that exits the current procedure. */
data class StopNode(
    override val position: Position
) : Node()

/** Represents an `OUTPUT` command that returns a value from a procedure. */
data class OutputNode(
    val value: Node,
    override val position: Position
) : Node()
