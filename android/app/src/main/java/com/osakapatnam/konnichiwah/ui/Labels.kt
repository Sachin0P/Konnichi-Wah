package com.osakapatnam.konnichiwah.ui

import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.Interest
import com.osakapatnam.konnichiwah.data.remote.JapaneseLevel
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

// All user-facing labels for the questionnaire answers live here, in one
// place, so that adding Hindi later means moving this file's strings into
// strings.xml rather than hunting through every screen.

val Interest.label: String
    get() = when (this) {
        Interest.TEMPLES_SHRINES -> "Temples & shrines"
        Interest.ANIME -> "Anime & pop culture"
        Interest.FOOD -> "Food"
        Interest.NATURE -> "Nature"
        Interest.SHOPPING -> "Shopping"
        Interest.SNOW -> "Snow & winter"
        Interest.NIGHTLIFE -> "Nightlife"
        Interest.HISTORY -> "History"
    }

val BudgetBand.label: String
    get() = when (this) {
        BudgetBand.LAKH_1_TO_2 -> "₹1–2 lakh"
        BudgetBand.LAKH_2_TO_3 -> "₹2–3 lakh"
        BudgetBand.LAKH_3_PLUS -> "₹3 lakh+"
    }

val Pace.label: String
    get() = when (this) {
        Pace.RELAXED -> "Relaxed"
        Pace.BALANCED -> "Balanced"
        Pace.PACKED -> "Packed"
    }

val GroupType.label: String
    get() = when (this) {
        GroupType.SOLO -> "Solo"
        GroupType.COUPLE -> "Couple"
        GroupType.FRIENDS -> "Friends"
        GroupType.FAMILY_KIDS -> "Family with kids"
        GroupType.FAMILY_ELDERS -> "Family with elders"
    }

val Diet.label: String
    get() = when (this) {
        Diet.VEG -> "Vegetarian"
        Diet.JAIN -> "Jain"
        Diet.EGGETARIAN -> "Eggetarian"
        Diet.HALAL -> "Halal"
        Diet.NON_VEG -> "Non-vegetarian"
    }

val AvoidItem.label: String
    get() = when (this) {
        AvoidItem.BEEF -> "Beef"
        AvoidItem.PORK -> "Pork"
        AvoidItem.SEAFOOD -> "Seafood"
        AvoidItem.EGG -> "Egg"
        AvoidItem.ONION_GARLIC -> "Onion & garlic"
    }

val SpiceLevel.label: String
    get() = when (this) {
        SpiceLevel.MILD -> "Mild"
        SpiceLevel.MEDIUM -> "Medium"
        SpiceLevel.INDIAN_SPICY -> "Indian spicy"
    }

val JapaneseLevel.label: String
    get() = when (this) {
        JapaneseLevel.NONE -> "None"
        JapaneseLevel.BASIC -> "A little"
    }

// monthLabel(4) == "Apr".
fun monthLabel(month: Int): String = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

// Labels for response values. These are plain strings from the backend, so
// fall back to the raw value for anything we don't recognise yet.
fun kindLabel(kind: String): String = when (kind) {
    "place" -> "Place"
    "activity" -> "Activity"
    "food" -> "Food"
    else -> kind
}

fun placeTypeLabel(placeType: String): String = placeType.replaceFirstChar { it.uppercase() }
