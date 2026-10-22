package com.osakapatnam.konnichiwah.ui.questionnaire

import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.Interest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireUiStateTest {

    @Test
    fun eachStepNeedsItsRequiredAnswers() {
        val empty = QuestionnaireUiState()

        assertFalse(empty.copy(step = Step.INTERESTS).canContinue)
        assertTrue(empty.copy(step = Step.INTERESTS, interests = setOf(Interest.FOOD)).canContinue)

        assertTrue(empty.copy(step = Step.TRIP).canContinue)

        assertFalse(empty.copy(step = Step.STYLE, budgetBand = BudgetBand.LAKH_1_TO_2).canContinue)
        assertTrue(
            empty.copy(step = Step.STYLE, budgetBand = BudgetBand.LAKH_1_TO_2, groupType = GroupType.SOLO).canContinue,
        )

        assertFalse(empty.copy(step = Step.TASTE).canContinue)
        assertTrue(empty.copy(step = Step.TASTE, diet = Diet.VEG).canContinue)
    }

    @Test
    fun notesLimitCountsBytesNotCharacters() {
        // "₹" is 3 bytes in UTF-8, so 334 of them (1002 bytes) is over the
        // backend's 1000-byte limit even though it's only 334 characters.
        val state = QuestionnaireUiState(step = Step.EXTRAS, notes = "₹".repeat(334))

        assertEquals(1002, state.notesBytes)
        assertFalse(state.canContinue)
    }

    @Test
    fun toRequestTrimsNotesAndSortsSelections() {
        val state = QuestionnaireUiState(
            interests = setOf(Interest.HISTORY, Interest.ANIME),
            budgetBand = BudgetBand.LAKH_3_PLUS,
            groupType = GroupType.COUPLE,
            diet = Diet.NON_VEG,
            notes = "  honeymoon  ",
            tripDays = 10,
            travelMonth = 11,
        )

        val request = state.toRequest()

        assertEquals(listOf(Interest.ANIME, Interest.HISTORY), request.profile.interests)
        assertEquals("honeymoon", request.profile.notes)
        assertEquals(10, request.tripDays)
        assertEquals(11, request.travelMonth)
    }
}
