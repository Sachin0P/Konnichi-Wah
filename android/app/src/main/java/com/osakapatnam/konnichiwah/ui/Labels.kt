package com.osakapatnam.konnichiwah.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.osakapatnam.konnichiwah.R
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

// The string resource for each questionnaire answer. The words themselves
// are in res/values/strings.xml (English) and res/values-hi (Hindi).

@get:StringRes
val Interest.labelRes: Int
    get() = when (this) {
        Interest.TEMPLES_SHRINES -> R.string.interest_temples
        Interest.ANIME -> R.string.interest_anime
        Interest.FOOD -> R.string.interest_food
        Interest.NATURE -> R.string.interest_nature
        Interest.SHOPPING -> R.string.interest_shopping
        Interest.SNOW -> R.string.interest_snow
        Interest.NIGHTLIFE -> R.string.interest_nightlife
        Interest.HISTORY -> R.string.interest_history
    }

@get:StringRes
val BudgetBand.labelRes: Int
    get() = when (this) {
        BudgetBand.LAKH_1_TO_2 -> R.string.budget_1_2
        BudgetBand.LAKH_2_TO_3 -> R.string.budget_2_3
        BudgetBand.LAKH_3_PLUS -> R.string.budget_3_plus
    }

@Composable
fun budgetBandDynamicLabel(band: BudgetBand, tripDays: Int): String {
    val d = tripDays.coerceIn(1, 30)
    return when (band) {
        BudgetBand.LAKH_1_TO_2 -> {
            val min = (d * 0.08).coerceAtLeast(0.5)
            val max = (d * 0.13).coerceAtLeast(0.8)
            "${formatLakh(min)}–${formatLakh(max)}"
        }
        BudgetBand.LAKH_2_TO_3 -> {
            val min = (d * 0.13).coerceAtLeast(0.8)
            val max = (d * 0.20).coerceAtLeast(1.2)
            "${formatLakh(min)}–${formatLakh(max)}"
        }
        BudgetBand.LAKH_3_PLUS -> {
            val min = (d * 0.22).coerceAtLeast(1.5)
            "${formatLakh(min)}+"
        }
    }
}

@Composable
private fun formatLakh(amount: Double): String {
    val rounded = Math.round(amount * 10.0) / 10.0
    val isHindi = currentLocale().language == "hi"
    val lakhWord = if (isHindi) "लाख" else "lakh"
    val kWord = if (isHindi) "हज़ार" else "k"

    return if (rounded < 1.0) {
        if (isHindi) "₹${Math.round(rounded * 100)} $kWord" else "₹${Math.round(rounded * 100)}k"
    } else if (rounded == rounded.toLong().toDouble()) {
        "₹${rounded.toLong()} $lakhWord"
    } else {
        "₹${String.format(java.util.Locale.US, "%.1f", rounded)} $lakhWord"
    }
}

@get:StringRes
val Pace.labelRes: Int
    get() = when (this) {
        Pace.RELAXED -> R.string.pace_relaxed
        Pace.BALANCED -> R.string.pace_balanced
        Pace.PACKED -> R.string.pace_packed
    }

@get:StringRes
val GroupType.labelRes: Int
    get() = when (this) {
        GroupType.SOLO -> R.string.group_solo
        GroupType.COUPLE -> R.string.group_couple
        GroupType.FRIENDS -> R.string.group_friends
        GroupType.FAMILY_KIDS -> R.string.group_family_kids
        GroupType.FAMILY_ELDERS -> R.string.group_family_elders
    }

@get:StringRes
val Diet.labelRes: Int
    get() = when (this) {
        Diet.VEG -> R.string.diet_veg
        Diet.JAIN -> R.string.diet_jain
        Diet.EGGETARIAN -> R.string.diet_eggetarian
        Diet.HALAL -> R.string.diet_halal
        Diet.NON_VEG -> R.string.diet_non_veg
    }

@get:StringRes
val AvoidItem.labelRes: Int
    get() = when (this) {
        AvoidItem.BEEF -> R.string.avoid_beef
        AvoidItem.PORK -> R.string.avoid_pork
        AvoidItem.SEAFOOD -> R.string.avoid_seafood
        AvoidItem.EGG -> R.string.avoid_egg
        AvoidItem.ONION_GARLIC -> R.string.avoid_onion_garlic
        AvoidItem.ALCOHOL -> R.string.avoid_alcohol
    }

@get:StringRes
val SpiceLevel.labelRes: Int
    get() = when (this) {
        SpiceLevel.MILD -> R.string.spice_mild
        SpiceLevel.MEDIUM -> R.string.spice_medium
        SpiceLevel.INDIAN_SPICY -> R.string.spice_indian
    }

@get:StringRes
val JapaneseLevel.labelRes: Int
    get() = when (this) {
        JapaneseLevel.NONE -> R.string.japanese_none
        JapaneseLevel.BASIC -> R.string.japanese_basic
    }

// monthLabel(4, Locale.ENGLISH) == "Apr".
fun monthLabel(month: Int, locale: Locale): String = Month.of(month).getDisplayName(TextStyle.SHORT, locale)

// Labels for response values. These are plain strings from the backend, so
// fall back to the raw value for anything we don't recognise yet. Each comes
// in two forms: one for screens (it follows the language on screen) and one
// taking Resources, for notifications.

fun kindLabel(res: Resources, kind: String): String = label(res, kind, kindLabels)
fun placeTypeLabel(res: Resources, placeType: String): String = label(res, placeType, placeTypeLabels)
fun timeSlotLabel(res: Resources, timeSlot: String): String = label(res, timeSlot, timeSlotLabels)

@Composable
fun kindLabel(kind: String): String = kindLabel(LocalResources.current, kind)

@Composable
fun placeTypeLabel(placeType: String): String = placeTypeLabel(LocalResources.current, placeType)

@Composable
fun timeSlotLabel(timeSlot: String): String = timeSlotLabel(LocalResources.current, timeSlot)

private val kindLabels = mapOf(
    "place" to R.string.kind_place,
    "activity" to R.string.kind_activity,
    "food" to R.string.kind_food,
)

private val placeTypeLabels = mapOf(
    "temple" to R.string.place_temple,
    "shrine" to R.string.place_shrine,
    "onsen" to R.string.place_onsen,
    "ryokan" to R.string.place_ryokan,
    "restaurant" to R.string.place_restaurant,
    "train" to R.string.place_train,
    "taxi" to R.string.place_taxi,
    "shop" to R.string.place_shop,
    "street" to R.string.place_street,
    "park" to R.string.place_park,
    "attraction" to R.string.place_attraction,
)

private val timeSlotLabels = mapOf(
    "breakfast" to R.string.slot_breakfast,
    "morning" to R.string.slot_morning,
    "lunch" to R.string.slot_lunch,
    "afternoon" to R.string.slot_afternoon,
    "evening" to R.string.slot_evening,
    "dinner" to R.string.slot_dinner,
)

private fun label(res: Resources, value: String, labels: Map<String, Int>): String =
    labels[value]?.let(res::getString) ?: value.replaceFirstChar { it.uppercase() }
