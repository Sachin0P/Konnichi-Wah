package com.osakapatnam.konnichiwah.ui.directions

import androidx.compose.ui.graphics.Color
import com.osakapatnam.konnichiwah.data.remote.JourneyLegDto
import com.osakapatnam.konnichiwah.data.remote.JourneyStopDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGuideTest {

    @Test
    fun sampleGuideHasFourSteps() {
        val steps = sampleGuide
        assertEquals(4, steps.size)

        assertEquals("Find this sign", steps[0].title)
        assertTrue(steps[0].visual is GuideVisual.SignArrow)
        val s0 = steps[0].visual as GuideVisual.SignArrow
        assertEquals("G", s0.letter)
        assertEquals("01", s0.number)

        assertEquals("Board this train", steps[1].title)
        assertTrue(steps[1].visual is GuideVisual.TrainFront)
        val s1 = steps[1].visual as GuideVisual.TrainFront
        assertEquals("Asakusa", s1.destEn)
        assertEquals("浅草", s1.destJp)

        assertEquals("Ride 17 more stops", steps[2].title)
        assertTrue(steps[2].visual is GuideVisual.Countdown)
        val s2 = steps[2].visual as GuideVisual.Countdown
        assertEquals(17, s2.stopsLeft)
        assertEquals(18, s2.total)

        assertEquals("This is your stop", steps[3].title)
        assertTrue(steps[3].visual is GuideVisual.Arrived)
        val s3 = steps[3].visual as GuideVisual.Arrived
        assertEquals("G", s3.letter)
        assertEquals("19", s3.number)
    }

    @Test
    fun buildGuideFromLegExtractsCodesProperly() {
        val leg = JourneyLegDto(
            mode = "subway",
            line = "Ginza Line",
            lineJa = "銀座線",
            lineCode = "G",
            color = "#F39700",
            from = JourneyStopDto("Shibuya", "渋谷", "G01", 35.6580, 139.7016),
            to = JourneyStopDto("Asakusa", "浅草", "G19", 35.7126, 139.7983),
            departs = "09:00",
            arrives = "09:33",
            minutes = 33,
            stops = 18,
            exit = "1",
            via = listOf("Omotesando"),
        )

        val steps = buildGuideFromLeg(leg)
        assertEquals(4, steps.size)

        val sign = steps[0].visual as GuideVisual.SignArrow
        assertEquals("G", sign.letter)
        assertEquals("01", sign.number)

        val train = steps[1].visual as GuideVisual.TrainFront
        assertEquals("Asakusa", train.destEn)
        assertEquals("浅草", train.destJp)

        val countdown = steps[2].visual as GuideVisual.Countdown
        assertEquals(17, countdown.stopsLeft)
        assertEquals(18, countdown.total)

        val arrived = steps[3].visual as GuideVisual.Arrived
        assertEquals("G", arrived.letter)
        assertEquals("19", arrived.number)
    }
}
