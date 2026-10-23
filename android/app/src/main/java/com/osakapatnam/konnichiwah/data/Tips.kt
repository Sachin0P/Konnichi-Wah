package com.osakapatnam.konnichiwah.data

import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.StopDto

// The most tips we show for one stop. Matches the backend's maxTipsPerStop.
const val MAX_TIPS_PER_STOP = 5

// The rulebook indexed for quick lookups. Built once from the bundled
// etiquette.json, so tips work with no network.
class Rulebook(rules: List<EtiquetteRuleDto>) {
    private val byId: Map<String, EtiquetteRuleDto> = rules.associateBy { it.id }

    // Rules for each place type, ordered exactly as the backend orders them
    // (GET /v1/etiquette): important first, then rules written mainly for
    // that place type (it's listed first), then by id.
    private val byPlaceType: Map<String, List<EtiquetteRuleDto>> =
        rules.flatMap { rule -> rule.placeTypes.map { it to rule } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (placeType, rulesForType) ->
                rulesForType.sortedWith(
                    compareBy<EtiquetteRuleDto> { if (it.severity == "important") 0 else 1 }
                        .thenBy { if (it.placeTypes.firstOrNull() == placeType) 0 else 1 }
                        .thenBy { it.id },
                )
            }

    val size: Int get() = byId.size

    // The tips for a stop. The server's tip ids win. A stop added offline
    // has none yet, so fall back to the same lookup the server will do when
    // it syncs (backend/cmd/api/itineraries.go attachTips): the place type's
    // rules that apply in the stop's city. Ids missing from our bundled copy
    // (the server's rulebook can be newer than this APK) are skipped, and the
    // city check also cleans up ids from before a rule was limited to a city.
    fun tipsFor(stop: StopDto): List<EtiquetteRuleDto> {
        val fromServer = stop.tipIds.mapNotNull { byId[it] }.filter { it.appliesIn(stop.city) }
        if (fromServer.isNotEmpty()) return fromServer

        return byPlaceType[stop.placeType].orEmpty()
            .filter { it.appliesIn(stop.city) }
            .take(MAX_TIPS_PER_STOP)
    }
}
