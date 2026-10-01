package com.example.byteshare

import com.example.byteshare.logic.FameEngine
import com.example.byteshare.logic.AppUsage
import com.example.byteshare.logic.MemberUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FameEngineTest {

    @Test
    fun multipliersMatchFourMemberExampleAndSumToMemberCount() {
        assertEquals(listOf(0.5, 0.8, 1.2, 1.5), FameEngine.multipliersFor(4))
        assertEquals(listOf(1.0), FameEngine.multipliersFor(1))

        for (count in 2..20) {
            val multipliers = FameEngine.multipliersFor(count)
            assertEquals(count.toDouble(), multipliers.sum(), 1e-9)
            assertTrue(multipliers.all { it in 0.5..1.5 })
            assertTrue(multipliers.zipWithNext().all { (left, right) -> left <= right })
        }
    }

    @Test
    fun shareAllocationAlwaysCoversBillToThePaise() {
        for (count in 1..20) {
            val shares = FameEngine.allocateSharesMinorUnits(2000.01, FameEngine.multipliersFor(count))
            assertEquals(200001L, shares.sum())
            assertTrue(shares.all { it >= 0L })
        }
    }

    @Test
    fun rankedMembersKeepExactMinorUnitTotal() {
        val usages = listOf(
            MemberUsage("a", listOf(AppUsage("Study", "productive", 100.0))),
            MemberUsage("b", listOf(AppUsage("Maps", "neutral", 100.0))),
            MemberUsage("c", listOf(AppUsage("Video", "stream", 100.0))),
            MemberUsage("d", listOf(AppUsage("Social", "social", 100.0)))
        )

        val ranked = FameEngine.rankGroup(usages, 2000.01)
        assertEquals(200001L, ranked.sumOf { it.shareMinorUnits })
        assertEquals(listOf(0.5, 0.8, 1.2, 1.5), ranked.sortedBy { it.rank }.map { it.multiplier })
    }

    @Test
    fun challengeReductionIsRedistributedAndRespectsMultiplierBounds() {
        val baseline = FameEngine.multipliersFor(4)
        val changed = FameEngine.applyMultiplierReductions(
            baseline,
            listOf(0.0, 0.1, 0.0, 0.0)
        )

        assertEquals(4.0, changed.sum(), 1e-9)
        assertTrue(changed.all { it in 0.5..1.5 })
        assertTrue(changed[1] < baseline[1])
        assertTrue(changed[0] > baseline[0])
        assertTrue(changed[2] > baseline[2])
    }

    @Test
    fun twoMemberCrewCannotReduceBelowFloorWithoutUnfundedDiscount() {
        val baseline = FameEngine.multipliersFor(2)
        val changed = FameEngine.applyMultiplierReductions(
            baseline,
            listOf(0.2, 0.0)
        )

        assertEquals(baseline, changed)
        assertEquals(2.0, changed.sum(), 1e-9)
    }
}
