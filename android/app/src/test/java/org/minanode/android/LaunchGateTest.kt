package org.minanode.android

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LaunchGateTest {
    @After fun clean() { LaunchGate.clear() }

    @Test fun everyProducerLaunchNeedsANewConfirmation() {
        val profile = NodeProfile(producer = true)
        assertTrue(runCatching { LaunchGate.offer(profile, null, 0) }.isFailure)
        val password = "test password".toCharArray()
        val id = LaunchGate.offer(profile, password, 0)
        val accepted = requireNotNull(LaunchGate.take(id, 1))
        assertNull(LaunchGate.take(id, 2))
        accepted.close()
        assertTrue(password.all { it == '\u0000' })
        assertTrue(runCatching { LaunchGate.offer(profile, null, 3) }.isFailure)
    }

    @Test fun serviceUsesTheExactConfirmedProfileDespiteLaterEdits() {
        val confirmed = NodeProfile(MinaNetwork.MAINNET, producer = true, maxPeers = 20)
        val id = LaunchGate.offer(confirmed, charArrayOf('x'), 0)
        val laterDraft = confirmed.copy(producer = false, maxPeers = 100)
        val accepted = requireNotNull(LaunchGate.take(id, 1))
        assertTrue(accepted.profile.producer)
        assertEquals(20, accepted.profile.maxPeers)
        assertNotEquals(laterDraft, accepted.profile)
        accepted.close()
    }

    @Test fun staleOrMissingIntentCannotBorrowAnotherLaunchPassword() {
        val id = LaunchGate.offer(NodeProfile(producer = true), charArrayOf('x'), 0)
        assertNull(LaunchGate.take(null, 1))
        assertNull(LaunchGate.take("old-launch", 1))
        requireNotNull(LaunchGate.take(id, 1)).close()
    }

    @Test fun expiredCancelledAndReplacedRequestsEraseTheirPasswords() {
        val first = charArrayOf('a')
        val oldId = LaunchGate.offer(NodeProfile(producer = true), first, 0)
        val second = charArrayOf('b')
        val newId = LaunchGate.offer(NodeProfile(producer = true), second, 1)
        assertTrue(first.all { it == '\u0000' })
        assertNull(LaunchGate.take(oldId, 2))
        LaunchGate.revoke(newId)
        assertTrue(second.all { it == '\u0000' })
        assertNull(LaunchGate.take(newId, 3))
        val third = charArrayOf('c')
        val expiredId = LaunchGate.offer(NodeProfile(producer = true), third, 0)
        assertNull(LaunchGate.take(expiredId, 60_000_000_001L))
        assertTrue(third.all { it == '\u0000' })
    }

    @Test fun closingOldServiceCannotEraseAnAlreadyAcceptedLaunch() {
        val password = charArrayOf('x')
        val id = LaunchGate.offer(NodeProfile(producer = true), password, 0)
        val accepted = requireNotNull(LaunchGate.take(id, 1))
        LaunchGate.clear()
        assertEquals('x', password.single())
        accepted.close()
        assertEquals('\u0000', password.single())
    }
}
