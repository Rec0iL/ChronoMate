package com.example.chronomate

import com.example.chronomate.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightsAndClassesTest {
    private val custom = listOf(CustomWeight("Bio", 0.27f), CustomWeight("Tracer", 0.30f))

    @Test
    fun customWeightsAppearOnlyWhenWanted() {
        assertEquals(STANDARD_BB_WEIGHTS, availableWeights(WeightType.BB, custom, false).map { it.weight })
        val withCustom = availableWeights(WeightType.BB, custom, true)
        assertEquals(STANDARD_BB_WEIGHTS + listOf(0.27f, 0.30f), withCustom.map { it.weight })
        assertTrue(withCustom.takeLast(2).all { it.isCustom })
        assertEquals(listOf(0.27f, 0.30f), availableWeights(WeightType.CUSTOM, custom, false).map { it.weight })
    }

    @Test
    fun switchingTypeMovesSelectionIntoTheList() {
        val diablo = availableWeights(WeightType.DIABLO, custom, false, ".22")
        val first = pelletsFor(".22").first()
        assertEquals(first.massGrams.toFloat(), optionToKeep(0.20f, "", diablo)!!.weight)
        assertEquals(first.name, optionToKeep(0.20f, "", diablo)!!.pellet!!.name)
        // a custom weight stays selected when custom weights are appended
        assertEquals(0.27f, optionToKeep(0.27f, "", availableWeights(WeightType.DIABLO, custom, true, ".22"))!!.weight)
    }

    @Test
    fun diablosAreThePelletCatalogue() {
        assertEquals(63, PELLETS.size)
        assertEquals(PELLETS.size, CALIBERS.sumOf { pelletsFor(it.first).size })
        val opts = availableWeights(WeightType.DIABLO, emptyList(), false, ".177")
        // JSB Exact and Air Arms Field weigh the same: only the selected pellet is marked
        val twins = opts.filter { sameWeight(it.weight, 0.547f) }
        assertTrue(twins.size >= 2)
        val chosen = twins[1].pellet!!.name
        assertEquals(listOf(false, true), twins.take(2).map { isSelected(0.547f, chosen, it) })
        assertEquals(0.033, findPellet(1.175f, ".22")!!.bc!!, 1e-9)
    }

    @Test
    fun exportChoiceSurvivesNewShots() {
        assertEquals(10, shotsToInclude(EXPORT_CHOICE_DEFAULT, 20))
        assertEquals(3, shotsToInclude(EXPORT_CHOICE_DEFAULT, 3))
        assertEquals(1, shotsToInclude(EXPORT_CHOICE_DEFAULT, 0))
        val five = exportChoiceFor(5, 20)
        assertEquals(5, shotsToInclude(five, 23))
        val all = exportChoiceFor(20, 20)
        assertEquals(EXPORT_CHOICE_ALL, all)
        assertEquals(24, shotsToInclude(all, 24))
    }

    @Test
    fun complianceAtTheReferenceBb() {
        val ar = DEFAULT_WEAPON_CLASSES.first { it.name == "AR" }
        assertEquals(Compliance.AWAITING, complianceOf(ar, 0f))
        assertEquals(Compliance.LEGAL, complianceOf(ar, 122.0f))     // 1.49 J at 0.20 g
        assertEquals(Compliance.ILLEGAL, complianceOf(ar, 123.0f))   // 1.51 J
        assertEquals(0.25f, maxWeightGrams(1.5f, 109.54451f), 0.0005f)
    }

    @Test
    fun reorderingMovesOneItem() {
        val names = DEFAULT_WEAPON_CLASSES.map { it.name }
        assertEquals(listOf("AR", "LMG", "DMR", "BACKUP", "SNIPER"), names.moved(0, 3))
        assertEquals(listOf("SNIPER", "BACKUP", "AR", "LMG", "DMR"), names.moved(4, 0))
        assertEquals(names, names.moved(2, 2))
        assertEquals(names, names.moved(9, 0))
    }

    @Test
    fun activeClassLimit() {
        val data = ChronoData(selectedWeaponClass = "DMR")
        assertEquals(2.0f, data.activeJouleLimit())
        assertEquals(1.0f, ChronoData(selectedWeaponClass = "GONE").activeJouleLimit()) // falls back to first class
    }
}
