package dev.marko.lsp.logo.parser

import dev.marko.lsp.logo.lexer.Lexer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ParserTest {

    // Helper

    /** Lex + parse a LOGO source string and return the ProgramNode. */
    private fun parse(source: String): ProgramNode {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens).parseProgram()
    }

    /** Lex + parse, returning both the ProgramNode and the parser instance. */
    private fun parseWithParser(source: String): Pair<ProgramNode, Parser> {
        val tokens = Lexer(source).tokenize()
        val parser = Parser(tokens)
        val program = parser.parseProgram()
        return program to parser
    }

    // Simple builtin call

    @Test
    fun `simple builtin call FORWARD 50`() {
        val program = parse("FORWARD 50")
        assertEquals(1, program.statements.size)

        val call = program.statements[0] as ProcedureCallNode
        assertEquals("FORWARD", call.name)
        assertEquals(1, call.args.size)

        val arg = call.args[0] as NumberNode
        assertEquals(50.0, arg.value)
    }

    @Test
    fun `builtin call with variable argument`() {
        val program = parse("FORWARD :size")
        val call = program.statements[0] as ProcedureCallNode
        assertEquals("FORWARD", call.name)

        val arg = call.args[0] as VariableNode
        assertEquals("size", arg.name)
    }

    @Test
    fun `builtin call with multiple arguments`() {
        val program = parse("SETXY 100 200")
        val call = program.statements[0] as ProcedureCallNode
        assertEquals("SETXY", call.name)
        assertEquals(2, call.args.size)
        assertEquals(100.0, (call.args[0] as NumberNode).value)
        assertEquals(200.0, (call.args[1] as NumberNode).value)
    }

    // Procedure definition

    @Test
    fun `procedure definition with params and body`() {
        val source = """
            TO square :size
              FORWARD :size
              RIGHT 90
            END
        """.trimIndent()

        val program = parse(source)
        assertEquals(1, program.statements.size)

        val proc = program.statements[0] as ProcedureDefNode
        assertEquals("square", proc.name)
        assertEquals(listOf("size"), proc.params)
        assertEquals(2, proc.body.size)

        val fwd = proc.body[0] as ProcedureCallNode
        assertEquals("FORWARD", fwd.name)
        assertEquals("size", (fwd.args[0] as VariableNode).name)

        val rt = proc.body[1] as ProcedureCallNode
        assertEquals("RIGHT", rt.name)
        assertEquals(90.0, (rt.args[0] as NumberNode).value)
    }

    @Test
    fun `procedure definition with no params`() {
        val source = """
            TO greet
              PRINT "hello
            END
        """.trimIndent()

        val program = parse(source)
        val proc = program.statements[0] as ProcedureDefNode
        assertEquals("greet", proc.name)
        assertTrue(proc.params.isEmpty())
        assertEquals(1, proc.body.size)
    }

    // REPEAT

    @Test
    fun `REPEAT 4 with block`() {
        val program = parse("REPEAT 4 [FORWARD 50 RIGHT 90]")
        assertEquals(1, program.statements.size)

        val repeat = program.statements[0] as RepeatNode
        assertEquals(4.0, (repeat.count as NumberNode).value)
        assertEquals(2, repeat.body.size)

        val fwd = repeat.body[0] as ProcedureCallNode
        assertEquals("FORWARD", fwd.name)
        assertEquals(50.0, (fwd.args[0] as NumberNode).value)

        val rt = repeat.body[1] as ProcedureCallNode
        assertEquals("RIGHT", rt.name)
        assertEquals(90.0, (rt.args[0] as NumberNode).value)
    }

    @Test
    fun `REPEAT with variable count`() {
        val program = parse("REPEAT :n [FORWARD 10]")
        val repeat = program.statements[0] as RepeatNode
        assertEquals("n", (repeat.count as VariableNode).name)
    }

    // MAKE

    @Test
    fun `MAKE assigns variable`() {
        val program = parse("MAKE \"x 10")
        assertEquals(1, program.statements.size)

        val make = program.statements[0] as MakeNode
        assertEquals("x", make.varName)
        assertEquals(10.0, (make.value as NumberNode).value)
    }

    @Test
    fun `MAKE with expression value`() {
        val program = parse("MAKE \"total :a + :b")
        val make = program.statements[0] as MakeNode
        assertEquals("total", make.varName)

        val expr = make.value as BinaryExprNode
        assertEquals(BinaryOp.PLUS, expr.op)
        assertEquals("a", (expr.left as VariableNode).name)
        assertEquals("b", (expr.right as VariableNode).name)
    }

    // IF

    @Test
    fun `IF with comparison condition`() {
        val program = parse("IF :x < 10 [FORWARD 50]")
        assertEquals(1, program.statements.size)

        val ifNode = program.statements[0] as IfNode

        val condition = ifNode.condition as BinaryExprNode
        assertEquals(BinaryOp.LESS, condition.op)
        assertEquals("x", (condition.left as VariableNode).name)
        assertEquals(10.0, (condition.right as NumberNode).value)

        assertEquals(1, ifNode.body.size)
        val fwd = ifNode.body[0] as ProcedureCallNode
        assertEquals("FORWARD", fwd.name)
    }

    @Test
    fun `IFELSE with two blocks`() {
        val program = parse("IFELSE :x > 0 [FORWARD 100] [BACK 100]")
        val ifElse = program.statements[0] as IfElseNode

        val cond = ifElse.condition as BinaryExprNode
        assertEquals(BinaryOp.GREATER, cond.op)

        assertEquals(1, ifElse.thenBody.size)
        assertEquals("FORWARD", (ifElse.thenBody[0] as ProcedureCallNode).name)

        assertEquals(1, ifElse.elseBody.size)
        assertEquals("BACK", (ifElse.elseBody[0] as ProcedureCallNode).name)
    }

    // FOR

    @Test
    fun `FOR loop with step`() {
        val program = parse("FOR [i 1 10 1] [PRINT :i]")
        assertEquals(1, program.statements.size)

        val forNode = program.statements[0] as ForNode
        assertEquals("i", forNode.variable)
        assertEquals(1.0, (forNode.start as NumberNode).value)
        assertEquals(10.0, (forNode.end as NumberNode).value)
        assertEquals(1.0, (forNode.step as NumberNode).value)

        assertEquals(1, forNode.body.size)
        val print = forNode.body[0] as ProcedureCallNode
        assertEquals("PRINT", print.name)
        assertEquals("i", (print.args[0] as VariableNode).name)
    }

    @Test
    fun `FOR loop without explicit step defaults to 1`() {
        val program = parse("FOR [i 1 10] [FORWARD :i]")
        val forNode = program.statements[0] as ForNode
        assertEquals(1.0, (forNode.step as NumberNode).value)
    }

    // DO_WHILE

    @Test
    fun `DO_WHILE loop`() {
        val program = parse("DO.WHILE [FORWARD 10] :x < 100")
        assertEquals(1, program.statements.size)

        val doWhile = program.statements[0] as DoWhileNode
        assertEquals(1, doWhile.body.size)

        val fwd = doWhile.body[0] as ProcedureCallNode
        assertEquals("FORWARD", fwd.name)

        val cond = doWhile.condition as BinaryExprNode
        assertEquals(BinaryOp.LESS, cond.op)
        assertEquals("x", (cond.left as VariableNode).name)
        assertEquals(100.0, (cond.right as NumberNode).value)
    }

    // STOP and OUTPUT

    @Test
    fun `STOP inside procedure`() {
        val source = """
            TO test :x
              IF :x < 0 [STOP]
              FORWARD :x
            END
        """.trimIndent()

        val program = parse(source)
        val proc = program.statements[0] as ProcedureDefNode

        val ifNode = proc.body[0] as IfNode
        assertTrue(ifNode.body[0] is StopNode)
    }

    @Test
    fun `OUTPUT returns value`() {
        val source = """
            TO double :x
              OUTPUT :x * 2
            END
        """.trimIndent()

        val program = parse(source)
        val proc = program.statements[0] as ProcedureDefNode
        val output = proc.body[0] as OutputNode

        val expr = output.value as BinaryExprNode
        assertEquals(BinaryOp.STAR, expr.op)
        assertEquals("x", (expr.left as VariableNode).name)
        assertEquals(2.0, (expr.right as NumberNode).value)
    }

    // Nested repeats

    @Test
    fun `nested REPEAT blocks`() {
        val source = """
            REPEAT 3 [
              REPEAT 4 [
                FORWARD 50
                RIGHT 90
              ]
              RIGHT 120
            ]
        """.trimIndent()

        val program = parse(source)
        val outer = program.statements[0] as RepeatNode
        assertEquals(3.0, (outer.count as NumberNode).value)
        assertEquals(2, outer.body.size) // inner REPEAT + RIGHT 120

        val inner = outer.body[0] as RepeatNode
        assertEquals(4.0, (inner.count as NumberNode).value)
        assertEquals(2, inner.body.size)
    }

    // Binary expression

    @Test
    fun `FORWARD with binary expression argument`() {
        val program = parse("FORWARD :x + 10")
        val call = program.statements[0] as ProcedureCallNode
        assertEquals("FORWARD", call.name)
        assertEquals(1, call.args.size)

        val expr = call.args[0] as BinaryExprNode
        assertEquals(BinaryOp.PLUS, expr.op)
        assertEquals("x", (expr.left as VariableNode).name)
        assertEquals(10.0, (expr.right as NumberNode).value)
    }

    @Test
    fun `chained binary expression is left-associative`() {
        val program = parse("FORWARD :a + :b - :c")
        val call = program.statements[0] as ProcedureCallNode
        val expr = call.args[0] as BinaryExprNode

        // Should be ((:a + :b) - :c)
        assertEquals(BinaryOp.MINUS, expr.op)
        assertEquals("c", (expr.right as VariableNode).name)

        val left = expr.left as BinaryExprNode
        assertEquals(BinaryOp.PLUS, left.op)
        assertEquals("a", (left.left as VariableNode).name)
        assertEquals("b", (left.right as VariableNode).name)
    }

    @Test
    fun `parenthesized expression`() {
        val program = parse("FORWARD (:x + :y) * 2")
        val call = program.statements[0] as ProcedureCallNode
        val expr = call.args[0] as BinaryExprNode

        assertEquals(BinaryOp.STAR, expr.op)
        assertEquals(2.0, (expr.right as NumberNode).value)

        val inner = expr.left as BinaryExprNode
        assertEquals(BinaryOp.PLUS, inner.op)
    }

    // Position tracking

    @Test
    fun `position tracking on nodes`() {
        val program = parse("FORWARD 50")
        val call = program.statements[0] as ProcedureCallNode
        assertEquals(1, call.position.line)
        assertEquals(1, call.position.column)

        val arg = call.args[0] as NumberNode
        assertEquals(1, arg.position.line)
        assertEquals(9, arg.position.column) // "FORWARD " is 8 chars, number starts at 9
    }

    @Test
    fun `position tracking across lines`() {
        val source = "FORWARD 50\nRIGHT 90"
        val program = parse(source)
        assertEquals(2, program.statements.size)

        val second = program.statements[1] as ProcedureCallNode
        assertEquals(2, second.position.line)
        assertEquals(1, second.position.column)
    }

    // Error recovery

    @Test
    fun `error recovery skips bad statement and continues`() {
        // The "@@@" line is unparseable; the next line is valid.
        val source = "@@@\nFORWARD 50"
        val (program, parser) = parseWithParser(source)

        // The valid FORWARD statement should be recovered
        assertEquals(1, program.statements.size)
        val call = program.statements[0] as ProcedureCallNode
        assertEquals("FORWARD", call.name)

        // There should be at least one error recorded
        assertTrue(parser.errors.isNotEmpty(), "Parser should record errors")
    }

    @Test
    fun `multiple errors with recovery`() {
        val source = "@@\n##\nFORWARD 100"
        val (program, parser) = parseWithParser(source)

        assertEquals(1, program.statements.size)
        assertTrue(parser.errors.size >= 2, "Parser should record multiple errors")
    }

    // User procedure call

    @Test
    fun `user procedure call with arguments`() {
        val program = parse("square 100")
        val call = program.statements[0] as ProcedureCallNode
        assertEquals("square", call.name)
        assertEquals(1, call.args.size)
        assertEquals(100.0, (call.args[0] as NumberNode).value)
    }

    @Test
    fun `user call with no arguments`() {
        val program = parse("greet")
        val call = program.statements[0] as ProcedureCallNode
        assertEquals("greet", call.name)
        assertTrue(call.args.isEmpty())
    }

    // Multiple statements

    @Test
    fun `multiple statements on separate lines`() {
        val source = """
            FORWARD 50
            RIGHT 90
            FORWARD 100
        """.trimIndent()

        val program = parse(source)
        assertEquals(3, program.statements.size)
        assertEquals("FORWARD", (program.statements[0] as ProcedureCallNode).name)
        assertEquals("RIGHT", (program.statements[1] as ProcedureCallNode).name)
        assertEquals("FORWARD", (program.statements[2] as ProcedureCallNode).name)
    }

    // String argument

    @Test
    fun `PRINT with string argument`() {
        val program = parse("PRINT \"hello")
        val call = program.statements[0] as ProcedureCallNode
        assertEquals("PRINT", call.name)
        assertEquals(1, call.args.size)

        val str = call.args[0] as StringNode
        assertEquals("hello", str.value)
    }

    // Full program

    @Test
    fun `full LOGO program with procedure and repeat`() {
        val source = """
            TO square :size
              REPEAT 4 [
                FORWARD :size
                RIGHT 90
              ]
            END
            square 100
        """.trimIndent()

        val program = parse(source)
        assertEquals(2, program.statements.size)

        val proc = program.statements[0] as ProcedureDefNode
        assertEquals("square", proc.name)

        val call = program.statements[1] as ProcedureCallNode
        assertEquals("square", call.name)
    }
}
