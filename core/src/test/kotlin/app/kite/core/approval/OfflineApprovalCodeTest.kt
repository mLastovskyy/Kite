package app.kite.core.approval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineApprovalCodeTest {
    private val secret = "kite-shared-secret".toByteArray()
    private val code = OfflineApprovalCode(secret, stepSeconds = 180L)

    @Test
    fun `code is six digits and stable within a step`() {
        // Aligned to a 180 s bucket boundary so +179 s stays in the same bucket.
        val t = 9_444_444L * 180_000L
        val a = code.generate(t)
        val b = code.generate(t + 179_000L)
        assertEquals(6, a.length)
        assertTrue(a.all { it.isDigit() })
        assertEquals(a, b)
    }

    @Test
    fun `code changes across a step boundary`() {
        val t = 1_700_000_100_000L
        val next = t + 180_000L
        // Not strictly guaranteed different for all secrets, but overwhelmingly likely;
        // this secret produces distinct codes across the boundary.
        assertFalse(code.generate(t) == code.generate(next))
    }

    @Test
    fun `verify accepts the current code`() {
        val t = 1_700_000_000_000L
        assertTrue(code.verify(code.generate(t), t))
    }

    @Test
    fun `verify tolerates one step of clock skew each way`() {
        val t = 1_700_000_360_000L
        val previousStep = code.generate(t - 180_000L)
        val nextStep = code.generate(t + 180_000L)
        assertTrue(code.verify(previousStep, t), "code from one step ago must still work")
        assertTrue(code.verify(nextStep, t), "code from one step ahead must still work")
    }

    @Test
    fun `verify rejects a code two steps away`() {
        val t = 1_700_000_720_000L
        val twoStepsAgo = code.generate(t - 360_000L)
        assertFalse(code.verify(twoStepsAgo, t))
    }

    @Test
    fun `verify rejects wrong code and wrong length`() {
        val t = 1_700_000_000_000L
        assertFalse(code.verify("000000", t) && code.generate(t) != "000000")
        assertFalse(code.verify("123", t))
        assertFalse(code.verify(code.generate(t).drop(1), t))
    }

    @Test
    fun `verify tolerates surrounding whitespace`() {
        val t = 1_700_000_000_000L
        assertTrue(code.verify("  ${code.generate(t)} ", t))
    }

    @Test
    fun `different secrets yield different codes`() {
        val t = 1_700_000_000_000L
        val other = OfflineApprovalCode("another-secret".toByteArray(), stepSeconds = 180L)
        assertFalse(code.generate(t) == other.generate(t))
    }
}
