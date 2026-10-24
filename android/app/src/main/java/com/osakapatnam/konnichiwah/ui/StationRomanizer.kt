package com.osakapatnam.konnichiwah.ui

/**
 * Ensures Japanese station names, hospital names, and places are properly
 * displayed in English with Hepburn romaji and clear facility suffixes.
 */
object JapaneseTransliterator {

    fun hasJapanese(s: String): Boolean = s.any {
        val code = it.code
        code in 0x3040..0x309F || code in 0x30A0..0x30FF || code in 0x4E00..0x9FFF
    }

    /**
     * Strips parentheses, company prefixes, and direction suffixes from a station name.
     * E.g. "神田(東京都)" -> "神田", "新宿(ＪＲ・小田急)" -> "新宿".
     */
    fun cleanStation(name: String): String {
        var s = name.trim()
        // Strip parenthesized metadata
        s = s.replace(Regex("""[\(（\[【〈〔][^\)）\]】〉〕]*[\)）\]】〉〕]"""), "").trim()

        val prefixes = listOf(
            "東京メトロ", "都営地下鉄", "都営", "JR東日本", "JR西日本", "JR東海", "JR",
            "東急", "京王", "小田急", "京成", "東武", "西武", "京急", "相鉄",
            "阪急", "阪神", "近鉄", "南海", "京阪",
        )
        for (p in prefixes) {
            if (s.startsWith(p) && s.length > p.length) {
                s = s.removePrefix(p).trim()
                break
            }
        }

        val suffixes = listOf("駅", "行き", "方面", "行")
        for (suf in suffixes) {
            if (s.endsWith(suf) && s.length > suf.length) {
                s = s.removeSuffix(suf).trim()
            }
        }
        return s.trim()
    }

    /**
     * Romanizes a Japanese station name to English Hepburn Romaji.
     */
    fun romanizeStation(name: String): String {
        val clean = cleanStation(name)
        if (clean.isBlank()) return name
        if (!hasJapanese(clean)) return clean

        stationDictionary[clean]?.let { return it }

        // Directional prefixes
        val dirPrefixes = listOf(
            "新" to "Shin-", "東" to "Higashi-", "西" to "Nishi-",
            "南" to "Minami-", "北" to "Kita-", "中" to "Naka-",
            "本" to "Hon-", "元" to "Moto-", "上" to "Kami-", "下" to "Shimo-",
        )
        for ((ja, en) in dirPrefixes) {
            if (clean.startsWith(ja) && clean.length > ja.length) {
                val rest = clean.removePrefix(ja)
                stationDictionary[rest]?.let { return en + it }
            }
        }

        // Station suffixes
        val suffixes = listOf(
            "大学前" to "-daigaku-mae", "前" to "-mae", "口" to "-guchi",
            "公園" to "-koen", "温泉" to "-onsen", "海岸" to "-kaigan",
            "通り" to "-dori", "通" to "-dori", "町" to "-cho",
        )
        for ((ja, en) in suffixes) {
            if (clean.endsWith(ja) && clean.length > ja.length) {
                val stem = clean.removeSuffix(ja)
                stationDictionary[stem]?.let { return it + en }
            }
        }

        // Multi-token decomposition e.g. "東京多摩" -> "Tokyo Tama"
        var decomposed = clean
        for ((ja, en) in stationDictionary.entries.sortedByDescending { it.key.length }) {
            if (decomposed.contains(ja)) {
                decomposed = decomposed.replace(ja, " $en ")
            }
        }
        val words = decomposed.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.isNotEmpty() && words.any { !hasJapanese(it) }) {
            val nonJp = words.filter { !hasJapanese(it) }
            if (nonJp.isNotEmpty()) {
                return nonJp.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            }
        }

        return clean
    }

    /**
     * Converts a Japanese medical, emergency, or everyday place name into English.
     */
    fun englishPlaceName(name: String, kind: String? = null): String {
        var s = name.trim()
        if (!hasJapanese(s)) return s

        s = s.replace("\u3000", " ").replace("・", " ").trim()

        // Strip corporate entity prefixes
        val corpPrefixes = listOf(
            "公益社団法人", "公益財団法人", "一般社団法人", "一般財団法人",
            "社会福祉法人", "特定非営利活動法人", "独立行政法人",
            "医療法人社団", "医療法人財団", "医療法人", "学校法人",
            "株式会社", "有限会社", "合同会社",
        )
        for (p in corpPrefixes) {
            if (s.startsWith(p)) {
                s = s.removePrefix(p).trim()
                val idx = s.indexOf("会")
                if (idx in 0..15) {
                    s = s.substring(idx + 1).trim()
                }
                break
            }
        }

        // Facility suffix detection
        val facilityMap = listOf(
            "大学医学部附属病院" to "University Hospital",
            "大学医学部付属病院" to "University Hospital",
            "医科大学附属病院" to "Medical University Hospital",
            "医科大学付属病院" to "Medical University Hospital",
            "大学附属病院" to "University Hospital",
            "大学病院" to "University Hospital",
            "総合病院" to "General Hospital",
            "記念病院" to "Memorial Hospital",
            "国際病院" to "International Hospital",
            "中央病院" to "Central Hospital",
            "市民病院" to "City Hospital",
            "病院" to "Hospital",
            "耳鼻咽喉科" to "ENT Clinic",
            "耳鼻科" to "ENT Clinic",
            "眼科" to "Eye Clinic",
            "歯科医院" to "Dental Clinic",
            "歯科クリニック" to "Dental Clinic",
            "歯科" to "Dental Clinic",
            "小児科" to "Pediatrics Clinic",
            "皮膚科" to "Dermatology Clinic",
            "皮フ科" to "Dermatology Clinic",
            "整形外科" to "Orthopedic Clinic",
            "形成外科" to "Plastic Surgery Clinic",
            "産婦人科" to "Maternity & OB/GYN Clinic",
            "内視鏡クリニック" to "Endoscopy Clinic",
            "消化器内科" to "Gastroenterology Clinic",
            "胃腸科" to "Gastroenterology Clinic",
            "循環器内科" to "Cardiovascular Clinic",
            "心療内科" to "Psychosomatic Clinic",
            "精神科" to "Psychiatric Clinic",
            "内科クリニック" to "Internal Medicine Clinic",
            "内科" to "Internal Medicine Clinic",
            "整骨院" to "Osteopathic Clinic",
            "接骨院" to "Orthopedic Bonesetting Clinic",
            "医院" to "Clinic",
            "クリニック" to "Clinic",
            "調剤薬局" to "Pharmacy",
            "ドラッグストア" to "Drugstore",
            "薬局" to "Pharmacy",
            "交番" to "Police Box",
            "警察署" to "Police Station",
            "郵便局" to "Post Office",
            "公衆トイレ" to "Public Restroom",
            "トイレ" to "Restroom",
        )

        var facilityType = ""
        var stem = s

        for ((ja, en) in facilityMap) {
            if (s.endsWith(ja)) {
                facilityType = en
                stem = s.removeSuffix(ja).trim()
                break
            }
        }

        if (facilityType.isEmpty()) {
            facilityType = when (kind) {
                "hospital" -> "Hospital"
                "clinic", "doctors" -> "Clinic"
                "pharmacy" -> "Pharmacy"
                "police" -> "Police Box"
                "toilet" -> "Restroom"
                "convenience_store" -> "Convenience Store"
                else -> ""
            }
        }

        // Clean stem
        stem = stem.replace(Regex("""[\(（\[【〈〔][^\)）\]】〉〕]*[\)）\]】〉〕]"""), "").trim()
        val annexes = listOf("本院", "分院", "本館", "別館", "新館", "東館", "西館", "南館", "北館", "A館", "B館")
        for (a in annexes) {
            stem = stem.removeSuffix(a).trim()
        }

        val stemEn = stationDictionary[stem] ?: romanizeStation(stem)
        return when {
            stemEn.isNotBlank() && facilityType.isNotBlank() -> {
                if (stemEn.endsWith(facilityType, ignoreCase = true)) stemEn else "$stemEn $facilityType"
            }
            facilityType.isNotBlank() -> facilityType
            stemEn.isNotBlank() -> stemEn
            else -> name
        }
    }

    /**
     * Resolves a stop into a clean English name and an optional Japanese Kanji name.
     */
    fun resolveStop(name: String, nameJa: String?): Pair<String, String> {
        val clean = cleanStation(name)
        val en = if (hasJapanese(clean)) romanizeStation(clean) else clean
        val ja = nameJa?.takeIf { it.isNotBlank() } ?: clean.takeIf { hasJapanese(clean) }.orEmpty()
        return Pair(en, ja)
    }

    private val stationDictionary = mapOf(
        "越後湯沢" to "Echigo-Yuzawa", "新横浜" to "Shin-Yokohama", "新神戸" to "Shin-Kobe",
        "広島" to "Hiroshima", "博多" to "Hakata", "仙台" to "Sendai", "盛岡" to "Morioka",
        "新青森" to "Shin-Aomori", "新函館北斗" to "Shin-Hakodate-Hokuto", "長野" to "Nagano",
        "金沢" to "Kanazawa", "敦賀" to "Tsuruga", "成田空港" to "Narita Airport",
        "羽田空港" to "Haneda Airport", "関西空港" to "Kansai Airport",
        "東京" to "Tokyo", "神田" to "Kanda", "秋葉原" to "Akihabara", "御徒町" to "Okachimachi",
        "上野" to "Ueno", "鶯谷" to "Uguisudani", "日暮里" to "Nippori", "西日暮里" to "Nishi-Nippori",
        "田端" to "Tabata", "駒込" to "Komagome", "巣鴨" to "Sugamo", "大塚" to "Otsuka",
        "池袋" to "Ikebukuro", "目白" to "Mejiro", "高田馬場" to "Takadanobaba", "新大久保" to "Shin-Okubo",
        "新宿" to "Shinjuku", "代々木" to "Yoyogi", "原宿" to "Harajuku", "渋谷" to "Shibuya",
        "恵比寿" to "Ebisu", "目黒" to "Meguro", "五反田" to "Gotanda", "大崎" to "Osaki",
        "品川" to "Shinagawa", "田町" to "Tamachi", "浜松町" to "Hamamatsucho", "新橋" to "Shimbashi",
        "有楽町" to "Yurakucho", "高輪ゲートウェイ" to "Takanawa Gateway", "東大" to "Todai",
        "御茶ノ水" to "Ochanomizu", "水道橋" to "Suidobashi", "飯田橋" to "Iidabashi", "市ヶ谷" to "Ichigaya",
        "四ツ谷" to "Yotsuya", "信濃町" to "Shinanomachi", "千駄ケ谷" to "Sendagaya", "大久保" to "Okubo",
        "中野" to "Nakano", "高円寺" to "Koenji", "阿佐ケ谷" to "Asagaya", "荻窪" to "Ogikubo",
        "西荻窪" to "Nishi-Ogikubo", "吉祥寺" to "Kichijoji", "三鷹" to "Mitaka", "国分寺" to "Kokubunji",
        "立川" to "Tachikawa", "八王子" to "Hachioji", "高尾" to "Takao", "本八幡" to "Motoyawata",
        "西船橋" to "Nishi-Funabashi", "船橋" to "Funabashi", "津田沼" to "Tsudanuma", "千葉" to "Chiba",
        "浅草" to "Asakusa", "銀座" to "Ginza", "日本橋" to "Nihombashi", "表参道" to "Omotesando",
        "六本木" to "Roppongi", "大手町" to "Otemachi", "北千住" to "Kita-Senju", "押上" to "Oshiage",
        "京都" to "Kyoto", "山科" to "Yamashina", "大阪" to "Osaka", "新大阪" to "Shin-Osaka",
        "梅田" to "Umeda", "難波" to "Namba", "なんば" to "Namba", "天王寺" to "Tennoji",
        "奈良" to "Nara", "近鉄奈良" to "Kintetsu-Nara", "三ノ宮" to "Sannomiya", "神戸" to "Kobe",
        "山本" to "Yamamoto", "山元" to "Yamamoto", "松岡" to "Matsuoka", "北村" to "Kitamura",
        "山脇" to "Yamawaki", "田中" to "Tanaka", "佐藤" to "Sato", "鈴木" to "Suzuki",
        "高橋" to "Takahashi", "渡辺" to "Watanabe", "伊藤" to "Ito", "中村" to "Nakamura",
        "小林" to "Kobayashi", "加藤" to "Kato", "吉田" to "Yoshida", "山田" to "Yamada",
        "牧田" to "Makita", "桑原" to "Kuwahara", "石川島" to "Ishikawajima", "石野" to "Ishino",
        "都倉" to "Tokura", "北山" to "Kitayama", "五木田" to "Gokita", "永富" to "Nagatomi",
        "まさき" to "Masaki", "みずもと" to "Mizumoto", "さいきょう" to "Saikyo", "岩田" to "Iwata",
        "伏見" to "Fushimi", "聖路加" to "St. Luke's", "多摩" to "Tama",
    )
}
