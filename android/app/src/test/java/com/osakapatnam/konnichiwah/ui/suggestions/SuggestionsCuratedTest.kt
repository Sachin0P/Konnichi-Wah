package com.osakapatnam.konnichiwah.ui.suggestions

import com.osakapatnam.konnichiwah.data.remote.CityPlanDto
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.data.remote.SuggestionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsCuratedTest {

    private fun place(id: String, city: String, kind: String = "place") = SuggestionDto(
        id = id,
        kind = kind,
        name = "Place $id",
        city = city,
        placeType = if (kind == "food") "restaurant" else "temple",
        reason = "A test place",
    )

    @Test
    fun relaxedPaceSelectsCuratedSubset() {
        val plan = PlanDto(
            cities = listOf(
                CityPlanDto("Tokyo", 3, "Capital"),
                CityPlanDto("Kyoto", 2, "Culture"),
            ),
            suggestions = listOf(
                place("t1", "Tokyo"), place("t2", "Tokyo"), place("t3", "Tokyo"),
                place("t4", "Tokyo"), place("t5", "Tokyo"), place("t6", "Tokyo"),
                place("t7", "Tokyo"), place("t8", "Tokyo"),
                place("tf1", "Tokyo", "food"), place("tf2", "Tokyo", "food"), place("tf3", "Tokyo", "food"),
                place("k1", "Kyoto"), place("k2", "Kyoto"), place("k3", "Kyoto"),
                place("k4", "Kyoto"), place("k5", "Kyoto"),
                place("kf1", "Kyoto", "food"), place("kf2", "Kyoto", "food"),
            ),
            source = "llm",
        )

        val selected = SuggestionsViewModel.selectCuratedSubset(plan, Pace.RELAXED, 5)

        // For Tokyo: 3 days * 2 sights = 6 sights + 2 food = 8
        // For Kyoto: 2 days * 2 sights = 4 sights + 2 food = 6
        // Total selected: 14 out of 18
        assertEquals(14, selected.size)
        assertTrue(selected.contains("t1"))
        assertTrue(selected.contains("t6"))
        assertTrue(!selected.contains("t7")) // 7th sight unselected
        assertTrue(!selected.contains("t8")) // 8th sight unselected
        assertTrue(selected.contains("tf1"))
        assertTrue(selected.contains("tf2"))
        assertTrue(!selected.contains("tf3")) // 3rd food unselected
    }

    @Test
    fun dayTripTownsAreGroupedWithBaseCity() {
        val plan = PlanDto(
            cities = listOf(CityPlanDto("Kyoto", 2, "Culture")),
            suggestions = listOf(
                place("k1", "Kyoto"),
                place("n1", "Nara"),
                place("k2", "Kyoto"),
                place("n2", "Nara"),
                place("k3", "Kyoto"),
                place("k4", "Kyoto"),
                place("k5", "Kyoto"),
            ),
            source = "llm",
        )

        // Kyoto (2 days) with relaxed pace (2 sights/day) -> target 4 sights
        val selected = SuggestionsViewModel.selectCuratedSubset(plan, Pace.RELAXED, 2)
        assertEquals(4, selected.size)
        assertTrue(selected.contains("k1"))
        assertTrue(selected.contains("n1"))
        assertTrue(selected.contains("k2"))
        assertTrue(selected.contains("n2"))
        assertTrue(!selected.contains("k3"))
    }
}
