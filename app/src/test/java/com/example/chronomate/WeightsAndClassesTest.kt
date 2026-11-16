package com.example.chronomate

import com.example.chronomate.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightsAndClassesTest {
    private val own = listOf(
        CustomWeight("Bio", 0.27f, kind = KIND_BB, id = "bio"),
        CustomWeight("HeavySniper", 0.69f, 4.5f, kind = KIND_PELLET, id = "hs")
    )
    private fun data(mode: BallisticMode = BallisticMode.AIRSOFT) = ChronoData(
        ballisticMode = mode, customWeights = own,
        activeAirsoft = normalizedActive(DEFAULT_ACTIVE_BB, KIND_BB, own),
        activePcp = normalizedActive(DEFAULT_ACTIVE_PCP, KIND_PELLET, own)
    )

    @Test
    fun activeListsPerModeWithOwnProjectiles() {
        assertEquals(DEFAULT_ACTIVE_BB + "own:bio", data().availableWeights().map { it.id })
        assertEquals(DEFAULT_ACTIVE_PCP + "own:hs", data(BallisticMode.PCP).availableWeights().map { it.id })
        assertTrue(DEFAULT_ACTIVE_PCP.all { data().resolve(it) != null })      // starter set exists
        // same ids as the desktop
        assertEquals("bb:0.20", DEFAULT_ACTIVE_BB.first())
        assertEquals("pellet:JSB Exact .177", DEFAULT_ACTIVE_PCP.first())
    }

    @Test
    fun libraryFilterAndSearch() {
        val bbs = library(KIND_BB)
        assertEquals("bb:0.12", bbs.first().id)
        assertEquals("bb:0.48", bbs.last().id)
        assertTrue(library(KIND_PELLET, ".22").all { it.pellet!!.caliber == ".22" })
        assertEquals(3, library(KIND_PELLET, ALL_CALIBERS, "baracuda match").size)
        assertEquals(63, PELLETS.size)
    }

    @Test
    fun selectionByIdNeverMarksTwo() {
        val d = data(BallisticMode.PCP)            // Baracuda Match .177 (0.690 g) is in the starter set
        val opts = d.availableWeights()
        val bara = opts.first { it.id == "pellet:H&N Baracuda Match .177" }
        assertEquals(listOf(bara.id), opts.filter { d.copy(selectedPcp = bara.id).isSelected(it) }.map { it.id })
        assertEquals(listOf("own:hs"), opts.filter { d.copy(selectedPcp = "own:hs").isSelected(it) }.map { it.id })
        // each mode has its own pick; Orga always uses the BB one
        val both = d.copy(selectedAirsoft = "bb:0.32", selectedPcp = bara.id)
        assertEquals("bb:0.32", both.orgaSelection().id)
        assertTrue(both.isSelected(both.resolve("bb:0.32")!!))
    }

    @Test
    fun keepSelectionWhenListChanges() {
        val pcp = data(BallisticMode.PCP).availableWeights()
        assertEquals(DEFAULT_ACTIVE_PCP.first(), optionToKeep("bb:0.20", 0.20f, pcp)!!.id)   // BB not offered: first
        assertEquals("pellet:H&N Baracuda Match .177", optionToKeep("gone", 0.69f, pcp)!!.id) // first with same weight
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

    @Test
    fun marshalVerdictAtFieldWeight() {
        // AR day, 0.32 g field weight; same numbers as the desktop's TestOrgaVerdict
        val opts = data().availableWeights().filter { !it.isCustom }
        val ar = judgeClass(DEFAULT_WEAPON_CLASSES.first { it.name == "AR" }, 99f, 0.32f, opts)
        assertEquals(Compliance.ILLEGAL, ar.status)
        assertEquals(0.306f, ar.maxWeightG, 0.001f)
        assertEquals(0.30f, ar.best!!.weight)
        val sniper = judgeClass(DEFAULT_WEAPON_CLASSES.first { it.name == "SNIPER" }, 99f, 0.32f, opts)
        assertEquals(Compliance.LEGAL, sniper.status)
        assertEquals(0.45f, sniper.best!!.weight)
        val backup = judgeClass(DEFAULT_WEAPON_CLASSES.first { it.name == "BACKUP" }, 150f, 0.32f, opts)
        assertEquals(Compliance.ILLEGAL, backup.status)
        assertEquals(null, backup.best)
    }
}
