package dev.marko.lsp.logo.features

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [SemanticTokensProvider].
 *
 * Each semantic token is encoded as 5 integers:
 * `[deltaLine, deltaStartChar, length, tokenTypeIndex, tokenModifiers]`
 *
 * Token type indices (matching the legend):
 *   0 = keyword, 1 = function, 2 = variable, 3 = number, 4 = string, 5 = comment
 */
class SemanticTokensProviderTest {

    private val provider = SemanticTokensProvider()

    // Helper

    /**
     * Decodes the flat data list into groups of 5 for easier assertion.
     * Each group: [deltaLine, deltaStartChar, length, typeIndex, modifiers]
     */
    private fun decodeTokens(data: List<Int>): List<List<Int>> {
        return data.chunked(5)
    }

    // 1. Empty input

    @Test
    fun `empty string produces empty data`() {
        val result = provider.provide("")
        assertTrue(result.data.isEmpty())
    }

    // 2. Single keyword token

    @Test
    fun `single keyword TO is encoded correctly`() {
        val result = provider.provide("TO")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size, "should produce exactly one token")
        val t = tokens[0]
        assertEquals(0, t[0], "deltaLine")
        assertEquals(0, t[1], "deltaStartChar (column 1 → 0-based 0)")
        assertEquals(2, t[2], "length of 'TO'")
        assertEquals(SemanticTokensProvider.TYPE_KEYWORD, t[3], "type = keyword")
        assertEquals(0, t[4], "modifiers")
    }

    @Test
    fun `END keyword is encoded as keyword`() {
        val result = provider.provide("END")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(SemanticTokensProvider.TYPE_KEYWORD, tokens[0][3])
    }

    @Test
    fun `REPEAT keyword is encoded as keyword`() {
        val result = provider.provide("REPEAT")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(SemanticTokensProvider.TYPE_KEYWORD, tokens[0][3])
        assertEquals(6, tokens[0][2], "length of 'REPEAT'")
    }

    // 3. Built-in function token

    @Test
    fun `builtin FD is encoded as function`() {
        val result = provider.provide("FD")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(SemanticTokensProvider.TYPE_FUNCTION, tokens[0][3], "type = function")
        assertEquals(2, tokens[0][2], "length of 'FD'")
    }

    @Test
    fun `builtin FORWARD is encoded as function`() {
        val result = provider.provide("FORWARD")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(SemanticTokensProvider.TYPE_FUNCTION, tokens[0][3])
        assertEquals(7, tokens[0][2])
    }

    // 4. Variable token

    @Test
    fun `variable colon-size is encoded as variable`() {
        val result = provider.provide(":size")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(SemanticTokensProvider.TYPE_VARIABLE, tokens[0][3], "type = variable")
        assertEquals(5, tokens[0][2], "length of ':size'")
    }

    // 5. Number token

    @Test
    fun `number 100 is encoded as number`() {
        val result = provider.provide("100")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(SemanticTokensProvider.TYPE_NUMBER, tokens[0][3], "type = number")
        assertEquals(3, tokens[0][2], "length of '100'")
    }

    // 6. String token

    @Test
    fun `string literal is encoded as string`() {
        val result = provider.provide("\"hello")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(SemanticTokensProvider.TYPE_STRING, tokens[0][3], "type = string")
        assertEquals(6, tokens[0][2], "length of '\"hello' (including quote)")
    }

    // 7. Delta encoding – two tokens same line

    @Test
    fun `two tokens on the same line have correct delta encoding`() {
        // "FD 100" → FD at col 1, 100 at col 4
        val result = provider.provide("FD 100")
        val tokens = decodeTokens(result.data)

        assertEquals(2, tokens.size)

        // First token: FD at (0, 0)
        assertEquals(0, tokens[0][0], "t1 deltaLine")
        assertEquals(0, tokens[0][1], "t1 deltaStartChar")
        assertEquals(2, tokens[0][2], "t1 length")
        assertEquals(SemanticTokensProvider.TYPE_FUNCTION, tokens[0][3])

        // Second token: 100 at (0, 3)
        // deltaLine = 0, deltaStartChar = 3 - 0 = 3
        assertEquals(0, tokens[1][0], "t2 deltaLine")
        assertEquals(3, tokens[1][1], "t2 deltaStartChar (3 - 0)")
        assertEquals(3, tokens[1][2], "t2 length")
        assertEquals(SemanticTokensProvider.TYPE_NUMBER, tokens[1][3])
    }

    // 8. Delta encoding – tokens on different lines

    @Test
    fun `tokens on different lines have correct delta encoding`() {
        // Line 1: FD 100
        // Line 2: RT 90
        val result = provider.provide("FD 100\nRT 90")
        val tokens = decodeTokens(result.data)

        assertEquals(4, tokens.size)

        // Token 0: FD at line 0, col 0
        assertEquals(0, tokens[0][0], "FD deltaLine")
        assertEquals(0, tokens[0][1], "FD deltaStartChar")
        assertEquals(SemanticTokensProvider.TYPE_FUNCTION, tokens[0][3])

        // Token 1: 100 at line 0, col 3
        assertEquals(0, tokens[1][0], "100 deltaLine")
        assertEquals(3, tokens[1][1], "100 deltaStartChar")
        assertEquals(SemanticTokensProvider.TYPE_NUMBER, tokens[1][3])

        // Token 2: RT at line 1, col 0
        // deltaLine = 1, deltaStartChar = 0 (absolute because new line)
        assertEquals(1, tokens[2][0], "RT deltaLine")
        assertEquals(0, tokens[2][1], "RT deltaStartChar (absolute on new line)")
        assertEquals(SemanticTokensProvider.TYPE_FUNCTION, tokens[2][3])

        // Token 3: 90 at line 1, col 3
        assertEquals(0, tokens[3][0], "90 deltaLine")
        assertEquals(3, tokens[3][1], "90 deltaStartChar")
        assertEquals(SemanticTokensProvider.TYPE_NUMBER, tokens[3][3])
    }

    // 9. Complete small program

    @Test
    fun `complete small program produces correct tokens`() {
        val source = """
            TO square :size
            REPEAT 4 [
            FD :size
            RT 90
            ]
            END
        """.trimIndent()

        val result = provider.provide(source)
        val tokens = decodeTokens(result.data)

        // Expected tokens (ignoring NEWLINE, brackets, operators):
        // Line 0: TO(keyword,0,2) square(function,3,6) :size(variable,10,5)
        // Line 1: REPEAT(keyword,0,6) 4(number,7,1)
        // Line 2: FD(function,0,2) :size(variable,3,5)
        // Line 3: RT(function,0,2) 90(number,3,2)
        // Line 4: (only ']' — no semantic token)
        // Line 5: END(keyword,0,3)

        assertTrue(tokens.size >= 9, "at least 9 semantic tokens expected, got ${tokens.size}")

        // Verify first token: TO keyword
        assertEquals(0, tokens[0][0], "TO deltaLine")
        assertEquals(0, tokens[0][1], "TO deltaStartChar")
        assertEquals(2, tokens[0][2], "TO length")
        assertEquals(SemanticTokensProvider.TYPE_KEYWORD, tokens[0][3])

        // Verify second token: square (IDENT → function)
        assertEquals(0, tokens[1][0], "square deltaLine")
        assertEquals(3, tokens[1][1], "square deltaStartChar")
        assertEquals(6, tokens[1][2], "square length")
        assertEquals(SemanticTokensProvider.TYPE_FUNCTION, tokens[1][3])

        // Verify third token: :size (variable)
        assertEquals(0, tokens[2][0], ":size deltaLine")
        assertEquals(7, tokens[2][1], ":size deltaStartChar (10 - 3)")
        assertEquals(5, tokens[2][2], ":size length")
        assertEquals(SemanticTokensProvider.TYPE_VARIABLE, tokens[2][3])

        // Verify REPEAT keyword is on next line
        assertEquals(1, tokens[3][0], "REPEAT deltaLine")
        assertEquals(0, tokens[3][1], "REPEAT deltaStartChar")
        assertEquals(6, tokens[3][2], "REPEAT length")
        assertEquals(SemanticTokensProvider.TYPE_KEYWORD, tokens[3][3])
    }

    // 10. NEWLINE and EOF are not emitted

    @Test
    fun `NEWLINE and EOF tokens are not included`() {
        // Just a newline should produce no semantic tokens
        val result = provider.provide("\n")
        assertTrue(result.data.isEmpty(), "only NEWLINE + EOF should produce no tokens")
    }

    // 11. UNKNOWN tokens are not emitted

    @Test
    fun `UNKNOWN tokens are not emitted`() {
        // A bare colon should produce UNKNOWN — we don't emit it
        val result = provider.provide(": ")
        assertTrue(result.data.isEmpty(), "UNKNOWN token should not be emitted")
    }

    // 12. Operators and brackets are not emitted

    @Test
    fun `operators and brackets are not emitted`() {
        // Only structural tokens — none should become semantic tokens
        val result = provider.provide("+ - * / < > = ( ) [ ]")
        assertTrue(result.data.isEmpty(), "operators and brackets should not produce tokens")
    }

    // 13. All keyword types

    @Test
    fun `all keywords produce type keyword`() {
        val keywords = listOf("TO", "END", "MAKE", "REPEAT", "FOR", "IF", "IFELSE", "DO.WHILE", "STOP", "OUTPUT")
        for (kw in keywords) {
            val result = provider.provide(kw)
            val tokens = decodeTokens(result.data)
            assertEquals(1, tokens.size, "keyword '$kw' should produce one token")
            assertEquals(SemanticTokensProvider.TYPE_KEYWORD, tokens[0][3], "'$kw' should be keyword type")
        }
    }

    // 14. Indented token has correct position

    @Test
    fun `indented token has correct 0-based column`() {
        // "    FD" → FD at column 5 (1-based), which is 4 (0-based)
        val result = provider.provide("    FD")
        val tokens = decodeTokens(result.data)

        assertEquals(1, tokens.size)
        assertEquals(0, tokens[0][0], "deltaLine")
        assertEquals(4, tokens[0][1], "deltaStartChar (1-based col 5 → 0-based 4)")
    }

    // 15. Data size is always a multiple of 5

    @Test
    fun `data list length is always multiple of 5`() {
        val sources = listOf(
            "",
            "FD 100",
            "TO square :size\nEND",
            "REPEAT 4 [FD 50 RT 90]"
        )
        for (src in sources) {
            val result = provider.provide(src)
            assertEquals(0, result.data.size % 5,
                "data size ${result.data.size} for '$src' should be multiple of 5")
        }
    }
}
