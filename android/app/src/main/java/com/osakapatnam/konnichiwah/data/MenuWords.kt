package com.osakapatnam.konnichiwah.data

import androidx.annotation.StringRes
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.UserProfileDto

// Hidden-ingredient warnings for the menu reader. We look for these words in
// the Japanese text the camera read, not in the machine translation: the
// translation is only a rough guide, but 豚 always means pork.
//
// The lists are hand-written, like the rulebook. Words for dishes (ラーメン,
// 味噌汁) are there because what's in them is rarely written on the menu:
// ramen broth and chashu are usually pork, and miso soup is made with dashi.
// Short hiragana words that turn up inside everyday words are left out
// (いか is also "how about", かに is in しずかに), so squid is only イカ.
// Have a native speaker check these before relying on them.

enum class MenuConcern(@StringRes val label: Int) {
    PORK(R.string.concern_pork),
    BEEF(R.string.concern_beef),
    CHICKEN(R.string.concern_chicken),
    MEAT(R.string.concern_meat),
    SEAFOOD(R.string.concern_seafood),
    DASHI(R.string.concern_dashi),
    EGG(R.string.concern_egg),
    ONION_GARLIC(R.string.concern_onion_garlic),
    ALCOHOL(R.string.concern_alcohol),
}

// One warning word found in a line of the menu, e.g. PORK because of "豚".
data class MenuFlag(val concern: MenuConcern, val word: String)

private val japaneseWords: Map<MenuConcern, List<String>> = mapOf(
    MenuConcern.PORK to listOf(
        "豚", "ポーク", "とんかつ", "トンカツ", "カツ丼", "かつ丼", "チャーシュー", "叉焼", "ベーコン", "ハム", "ソーセージ",
        "とんこつ", "豚骨", "ラード", "餃子", "ぎょうざ", "ギョーザ", "ラーメン", "らーめん", "拉麺", "お好み焼", "焼きそば",
    ),
    MenuConcern.BEEF to listOf("牛", "ビーフ", "ぎゅう", "すき焼", "しゃぶしゃぶ", "ハンバーグ", "焼肉", "焼き肉", "ステーキ"),
    MenuConcern.CHICKEN to listOf("鶏", "鳥", "チキン", "唐揚", "から揚", "からあげ", "親子丼"),
    MenuConcern.MEAT to listOf("肉", "ミート", "ラーメン", "らーめん", "拉麺", "カレー"),
    MenuConcern.SEAFOOD to listOf(
        "魚", "海老", "えび", "エビ", "蟹", "カニ", "イカ", "たこ焼", "タコ", "貝", "鮭", "サーモン", "鮪", "まぐろ",
        "マグロ", "刺身", "うなぎ", "鰻", "しらす", "ツナ", "明太", "たらこ", "いくら", "海鮮", "シーフード",
    ),
    MenuConcern.DASHI to listOf(
        "だし", "出汁", "ダシ", "鰹", "かつお", "カツオ", "煮干", "いりこ", "魚介", "味噌汁", "みそ汁", "うどん", "そば", "蕎麦",
        "おでん", "茶碗蒸し", "天つゆ", "天ぷら", "天丼", "お好み焼",
    ),
    MenuConcern.EGG to listOf("卵", "玉子", "たまご", "タマゴ", "親子丼", "オムライス", "茶碗蒸し", "マヨ"),
    MenuConcern.ONION_GARLIC to listOf(
        "玉ねぎ", "たまねぎ", "玉葱", "オニオン", "にんにく", "ニンニク", "大蒜", "ガーリック", "ねぎ", "ネギ", "葱", "にら", "ニラ", "韮",
    ),
    MenuConcern.ALCOHOL to listOf("酒", "みりん", "味醂", "ワイン", "ビール"),
)

// Words that contain a warning word but don't mean it: 牛乳 is milk and
// 牛蒡 is burdock root, not beef.
private val falseFriends: Map<MenuConcern, List<String>> = mapOf(
    MenuConcern.BEEF to listOf("牛乳", "牛蒡"),
)

// Menus often have English too. These are matched as whole words, so
// "eggplant" isn't egg.
private val englishWords: Map<MenuConcern, List<String>> = mapOf(
    MenuConcern.PORK to listOf("pork", "ham", "bacon", "sausage", "tonkotsu", "chashu", "ramen"),
    MenuConcern.BEEF to listOf("beef", "steak", "hamburg", "wagyu"),
    MenuConcern.CHICKEN to listOf("chicken", "karaage"),
    MenuConcern.MEAT to listOf("meat", "ramen", "curry"),
    MenuConcern.SEAFOOD to listOf("fish", "shrimp", "prawn", "crab", "squid", "octopus", "salmon", "tuna", "eel", "seafood", "sashimi"),
    MenuConcern.DASHI to listOf("dashi", "bonito", "udon", "soba", "miso soup", "tempura"),
    MenuConcern.EGG to listOf("egg", "omelette", "omelet", "mayo", "mayonnaise"),
    MenuConcern.ONION_GARLIC to listOf("onion", "garlic", "leek"),
    MenuConcern.ALCOHOL to listOf("sake", "mirin", "wine", "beer"),
)

private val englishPatterns: Map<MenuConcern, List<Pair<String, Regex>>> = englishWords.mapValues { (_, words) ->
    words.map { it to Regex("""\b${Regex.escape(it)}(s|es)?\b""", RegexOption.IGNORE_CASE) }
}

// Text recognition often mixes up the small ゜ and ゛ marks (it read 天ぷら
// as 天ぶら in testing), so we treat ぷ and ぶ (and the rest of the row, in
// hiragana and katakana) as the same letter when matching.
private val looksAlike = "ぱぴぷぺぽパピプペポ".zip("ばびぶべぼバビブベボ").toMap()

private fun foldMarks(s: String) = s.map { looksAlike[it] ?: it }.joinToString("")

// Every concern a line of menu text mentions, with the first word that gave
// it away. The general "meat" warning is left out when a line already names
// the meat, so ラーメン shows "Pork", not "Pork" and "Meat".
fun menuFlags(text: String): List<MenuFlag> {
    val flags = MenuConcern.entries.mapNotNull { concern ->
        val cleaned = foldMarks(falseFriends[concern].orEmpty().fold(text) { t, friend -> t.replace(friend, "") })

        val word = japaneseWords[concern].orEmpty().firstOrNull { foldMarks(it) in cleaned }
            ?: englishPatterns[concern].orEmpty().firstOrNull { (_, pattern) -> pattern.containsMatchIn(cleaned) }?.first

        word?.let { MenuFlag(concern, it) }
    }

    val namesTheMeat = flags.any { it.concern in setOf(MenuConcern.PORK, MenuConcern.BEEF, MenuConcern.CHICKEN) }
    return if (namesTheMeat) flags.filter { it.concern != MenuConcern.MEAT } else flags
}

// Which concerns matter to this traveller. With no profile saved, all of
// them do.
fun menuConcernsFor(profile: UserProfileDto?): Set<MenuConcern> {
    if (profile == null) return MenuConcern.entries.toSet()

    val vegetarian = setOf(
        MenuConcern.PORK, MenuConcern.BEEF, MenuConcern.CHICKEN, MenuConcern.MEAT, MenuConcern.SEAFOOD, MenuConcern.DASHI,
    )
    val concerns = when (profile.diet) {
        Diet.VEG -> vegetarian + MenuConcern.EGG
        Diet.EGGETARIAN -> vegetarian
        Diet.JAIN -> vegetarian + MenuConcern.EGG + MenuConcern.ONION_GARLIC
        // Any meat that isn't certified halal is worth asking about.
        Diet.HALAL -> setOf(MenuConcern.PORK, MenuConcern.ALCOHOL, MenuConcern.BEEF, MenuConcern.CHICKEN, MenuConcern.MEAT)
        Diet.NON_VEG -> emptySet()
    }.toMutableSet()

    for (item in profile.avoid) {
        concerns += when (item) {
            // A dish that just says "meat" could be either.
            AvoidItem.BEEF -> listOf(MenuConcern.BEEF, MenuConcern.MEAT)
            AvoidItem.PORK -> listOf(MenuConcern.PORK, MenuConcern.MEAT)
            // Dashi is fish stock.
            AvoidItem.SEAFOOD -> listOf(MenuConcern.SEAFOOD, MenuConcern.DASHI)
            AvoidItem.EGG -> listOf(MenuConcern.EGG)
            AvoidItem.ONION_GARLIC -> listOf(MenuConcern.ONION_GARLIC)
            AvoidItem.ALCOHOL -> listOf(MenuConcern.ALCOHOL)
        }
    }
    if (profile.drinksAlcohol == false) {
        concerns += MenuConcern.ALCOHOL
    }
    return concerns
}

// Text recognition puts spaces between the characters of vertical Japanese
// ("枝 豆", "野 菜 カレー"). Japanese doesn't use spaces between words, so
// they only get in the way: the translation reads 枝 豆 as "branch beans"
// rather than edamame, and "海 老" doesn't match the warning word 海老. Spaces
// next to English letters or numbers ("Pork cutlet", "¥ 850") are kept.
private val japaneseChar = """[\p{IsHan}\p{IsHiragana}\p{IsKatakana}ー々]"""
private val spaceInJapanese = Regex("(?<=$japaneseChar)\\s+(?=$japaneseChar)")

fun joinJapanese(text: String): String = text.replace(spaceInJapanese, "")
