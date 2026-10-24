package data

import (
	"strings"
	"unicode"
	"unicode/utf8"
)

// HasJapaneseRune reports whether s contains any Kanji, Hiragana, or Katakana characters.
func HasJapaneseRune(s string) bool {
	for _, r := range s {
		if unicode.In(r, unicode.Hiragana, unicode.Katakana, unicode.Han) {
			return true
		}
	}
	return false
}

// EnglishPlaceName converts a Japanese place name (especially hospitals, clinics,
// pharmacies, police boxes, etc.) into an accurate English name.
func EnglishPlaceName(name string, kind string) string {
	s := strings.TrimSpace(name)
	if !HasJapaneseRune(s) {
		return s
	}

	s = strings.ReplaceAll(s, "\u3000", " ")
	s = strings.ReplaceAll(s, "・", " ")

	// 1. Strip corporate/legal entity prefixes (e.g. 医療法人社団, 社会福祉法人)
	corpPrefixes := []string{
		"公益社団法人", "公益財団法人", "一般社団法人", "一般財団法人",
		"社会福祉法人", "特定非営利活動法人", "独立行政法人", "地方独立行政法人",
		"医療法人社団", "医療法人財団", "医療法人", "学校法人",
		"株式会社", "有限会社", "合同会社",
	}
	for _, p := range corpPrefixes {
		if strings.HasPrefix(s, p) {
			s = strings.TrimPrefix(s, p)
			// Strip trailing group name ending in 会 (e.g. 健育会, 洛和会)
			if idx := strings.Index(s, "会"); idx >= 0 && idx < 15 {
				s = strings.TrimSpace(s[idx+len("会"):])
			}
			break
		}
	}
	s = strings.TrimSpace(s)

	// 2. Identify and strip facility suffix to determine category
	type suffixRule struct {
		pattern string
		english string
	}

	suffixes := []suffixRule{
		// Hospitals
		{"大学医学部附属病院", "University Hospital"},
		{"大学医学部付属病院", "University Hospital"},
		{"医科大学附属病院", "Medical University Hospital"},
		{"医科大学付属病院", "Medical University Hospital"},
		{"大学附属病院", "University Hospital"},
		{"大学付属病院", "University Hospital"},
		{"大学病院", "University Hospital"},
		{"総合病院", "General Hospital"},
		{"記念病院", "Memorial Hospital"},
		{"国際病院", "International Hospital"},
		{"中央病院", "Central Hospital"},
		{"市民病院", "City Hospital"},
		{"都立病院", "Metropolitan Hospital"},
		{"府立病院", "Prefectural Hospital"},
		{"県立病院", "Prefectural Hospital"},
		{"病院", "Hospital"},

		// Specialized clinics
		{"耳鼻咽喉科医院", "ENT Clinic"},
		{"耳鼻咽喉科クリニック", "ENT Clinic"},
		{"耳鼻咽喉科", "ENT Clinic"},
		{"耳鼻科医院", "ENT Clinic"},
		{"耳鼻科クリニック", "ENT Clinic"},
		{"耳鼻科", "ENT Clinic"},

		{"眼科医院", "Eye Clinic"},
		{"眼科クリニック", "Eye Clinic"},
		{"眼科", "Eye Clinic"},

		{"歯科医院", "Dental Clinic"},
		{"歯科クリニック", "Dental Clinic"},
		{"小児歯科", "Pediatric Dental Clinic"},
		{"矯正歯科", "Orthodontic Clinic"},
		{"歯科", "Dental Clinic"},

		{"小児科医院", "Pediatrics Clinic"},
		{"小児科クリニック", "Pediatrics Clinic"},
		{"小児科", "Pediatrics Clinic"},

		{"皮膚科医院", "Dermatology Clinic"},
		{"皮膚科クリニック", "Dermatology Clinic"},
		{"皮膚科", "Dermatology Clinic"},
		{"皮フ科", "Dermatology Clinic"},

		{"整形外科医院", "Orthopedic Clinic"},
		{"整形外科クリニック", "Orthopedic Clinic"},
		{"整形外科", "Orthopedic Clinic"},
		{"形成外科", "Plastic Surgery Clinic"},

		{"産婦人科医院", "Maternity & OB/GYN Clinic"},
		{"産婦人科クリニック", "Maternity & OB/GYN Clinic"},
		{"産婦人科", "Maternity & OB/GYN Clinic"},
		{"産科", "Maternity Clinic"},
		{"婦人科", "Gynecology Clinic"},

		{"内視鏡クリニック", "Endoscopy Clinic"},
		{"内視鏡内科", "Endoscopy Clinic"},
		{"消化器内科クリニック", "Gastroenterology Clinic"},
		{"消化器内科", "Gastroenterology Clinic"},
		{"消化器科", "Gastroenterology Clinic"},
		{"胃腸科", "Gastroenterology Clinic"},

		{"循環器内科クリニック", "Cardiovascular Clinic"},
		{"循環器内科", "Cardiovascular Clinic"},
		{"循環器科", "Cardiovascular Clinic"},

		{"心療内科クリニック", "Psychosomatic Clinic"},
		{"心療内科", "Psychosomatic Clinic"},
		{"精神科クリニック", "Psychiatric Clinic"},
		{"精神科", "Psychiatric Clinic"},

		{"内科クリニック", "Internal Medicine Clinic"},
		{"内科医院", "Internal Medicine Clinic"},
		{"内科", "Internal Medicine Clinic"},

		{"整骨院", "Osteopathic Clinic"},
		{"接骨院", "Bonesetting & Orthopedic Clinic"},
		{"鍼灸院", "Acupuncture Clinic"},

		{"医院", "Clinic"},
		{"クリニック", "Clinic"},
		{"診療所", "Medical Clinic"},

		// Pharmacies
		{"調剤薬局", "Pharmacy"},
		{"ドラッグストア", "Drugstore"},
		{"薬局", "Pharmacy"},
		{"薬品", "Pharmacy"},
		{"薬店", "Pharmacy"},

		// Police
		{"地域安全センター", "Community Safety Center"},
		{"交番", "Police Box"},
		{"派出所", "Police Substation"},
		{"警察署", "Police Station"},

		// Everyday
		{"郵便局", "Post Office"},
		{"公衆便所", "Public Restroom"},
		{"公衆トイレ", "Public Restroom"},
		{"トイレ", "Restroom"},
		{"コンビニエンスストア", "Convenience Store"},
		{"コンビニ", "Convenience Store"},
	}

	facilityType := ""
	nameStem := s

	for _, suf := range suffixes {
		if strings.HasSuffix(s, suf.pattern) {
			facilityType = suf.english
			nameStem = strings.TrimSpace(strings.TrimSuffix(s, suf.pattern))
			break
		}
	}

	// If no facility suffix matched, determine by kind
	if facilityType == "" {
		switch kind {
		case "hospital":
			facilityType = "Hospital"
		case "clinic", "doctors":
			facilityType = "Clinic"
		case "pharmacy":
			facilityType = "Pharmacy"
		case "police":
			facilityType = "Police Box"
		case "atm":
			facilityType = "ATM"
		case "toilet":
			facilityType = "Public Restroom"
		case "convenience_store":
			facilityType = "Convenience Store"
		}
	}

	// 3. Clean and romanize the name stem
	nameStem = cleanPlaceStem(nameStem)
	if nameStem == "" {
		if facilityType != "" {
			return facilityType
		}
		return name
	}

	stemEn := romanizeJapaneseWord(nameStem)
	if stemEn == "" || stemEn == nameStem {
		if facilityType != "" {
			return facilityType
		}
		return name
	}

	if facilityType == "" {
		return stemEn
	}

	// If stem already ends with the facility type (e.g. "St. Luke's Hospital"), avoid duplication
	if strings.HasSuffix(strings.ToLower(stemEn), strings.ToLower(facilityType)) {
		return stemEn
	}

	return stemEn + " " + facilityType
}

func cleanPlaceStem(s string) string {
	// Strip parentheses e.g. (A館), (分院)
	for {
		start := -1
		end := -1
		for i, r := range s {
			if r == '(' || r == '（' || r == '[' || r == '【' || r == '〈' {
				start = i
			} else if (r == ')' || r == '）' || r == ']' || r == '】' || r == '〉') && start != -1 {
				end = i + utf8.RuneLen(r)
				break
			}
		}
		if start != -1 && end != -1 && end > start {
			s = strings.TrimSpace(s[:start] + s[end:])
		} else {
			break
		}
	}

	// Remove common building annex suffixes
	annexes := []string{"本院", "分院", "本館", "別館", "新館", "東館", "西館", "南館", "北館", "A館", "B館", "C館", "1号館", "2号館"}
	for _, a := range annexes {
		s = strings.TrimSuffix(s, a)
	}

	for _, grp := range []string{"健育会", "洛和会", "三幸会", "一心会", "一仁会"} {
		s = strings.TrimPrefix(s, grp)
		s = strings.TrimSpace(s)
	}

	return strings.TrimSpace(s)
}

// romanizeJapaneseWord converts Japanese Kanji, Katakana, and Hiragana into Hepburn romaji.
func romanizeJapaneseWord(s string) string {
	s = strings.TrimSpace(s)
	if s == "" {
		return ""
	}

	// Check if already Latin
	if !HasJapaneseRune(s) {
		return s
	}

	// Dictionary lookup for prominent locations and facility brands
	if val, ok := placeNameDict[s]; ok {
		return val
	}

	// Tokenize / replace known multi-character prefixes and components
	var result strings.Builder
	runes := []rune(s)
	i := 0
	for i < len(runes) {
		matched := false

		// Try 4, 3, 2, 1-character matches in dictionary
		for l := 5; l >= 1; l-- {
			if i+l <= len(runes) {
				sub := string(runes[i : i+l])
				if en, found := placeNameDict[sub]; found {
					if result.Len() > 0 && !strings.HasSuffix(result.String(), " ") && !strings.HasSuffix(result.String(), "-") {
						result.WriteString(" ")
					}
					result.WriteString(en)
					i += l
					matched = true
					break
				}
			}
		}

		if matched {
			continue
		}

		// Try katakana transliteration
		r := runes[i]
		if r >= 0x30A0 && r <= 0x30FF {
			katakanaEnd := i
			for katakanaEnd < len(runes) && runes[katakanaEnd] >= 0x30A0 && runes[katakanaEnd] <= 0x30FF {
				katakanaEnd++
			}
			kataStr := string(runes[i:katakanaEnd])
			kataEn := transliterateKatakana(kataStr)
			if result.Len() > 0 && !strings.HasSuffix(result.String(), " ") {
				result.WriteString(" ")
			}
			result.WriteString(kataEn)
			i = katakanaEnd
			continue
		}

		// Try hiragana transliteration
		if r >= 0x3040 && r <= 0x309F {
			hiraganaEnd := i
			for hiraganaEnd < len(runes) && runes[hiraganaEnd] >= 0x3040 && runes[hiraganaEnd] <= 0x309F {
				hiraganaEnd++
			}
			hiraStr := string(runes[i:hiraganaEnd])
			hiraEn := transliterateHiragana(hiraStr)
			if result.Len() > 0 && !strings.HasSuffix(result.String(), " ") {
				result.WriteString(" ")
			}
			result.WriteString(hiraEn)
			i = hiraganaEnd
			continue
		}

		// Single kanji fallback
		kanjiStr := string(r)
		if en, ok := placeNameDict[kanjiStr]; ok {
			if result.Len() > 0 && !strings.HasSuffix(result.String(), " ") {
				result.WriteString(" ")
			}
			result.WriteString(en)
		} else if unicode.IsLetter(r) && r <= 0x7F {
			result.WriteRune(r)
		} else if unicode.IsDigit(r) {
			result.WriteRune(r)
		}
		i++
	}

	out := strings.TrimSpace(result.String())
	// Capitalize words
	words := strings.Fields(out)
	for idx, w := range words {
		if len(w) > 0 {
			words[idx] = strings.ToUpper(w[:1]) + strings.ToLower(w[1:])
		}
	}
	return strings.Join(words, " ")
}

// Common place, medical, surname and geographic terms
var placeNameDict = map[string]string{
	// Cities and Regions
	"東京": "Tokyo", "京都": "Kyoto", "大阪": "Osaka", "奈良": "Nara", "札幌": "Sapporo",
	"新宿": "Shinjuku", "渋谷": "Shibuya", "銀座": "Ginza", "浅草": "Asakusa", "上野": "Ueno",
	"品川": "Shinagawa", "池袋": "Ikebukuro", "六本木": "Roppongi", "日本橋": "Nihombashi",
	"赤坂": "Akasaka", "目黒": "Meguro", "中野": "Nakano", "世田谷": "Setagaya", "杉並": "Suginami",
	"練馬": "Nerima", "文京": "Bunkyo", "千代田": "Chiyoda", "中央": "Chuo", "港": "Minato",
	"台東": "Taito", "墨田": "Sumida", "江東": "Koto", "大田": "Ota", "北区": "Kita-ku",
	"荒川": "Arakawa", "板橋": "Itabashi", "足立": "Adachi", "葛飾": "Katsushika", "江戸川": "Edogawa",
	"伏見": "Fushimi", "嵐山": "Arashiyama", "祇園": "Gion", "梅田": "Umeda", "難波": "Namba",
	"なんば": "Namba", "天王寺": "Tennoji", "新大阪": "Shin-Osaka", "神田": "Kanda", "秋葉原": "Akihabara",
	"有楽町": "Yurakucho", "新橋": "Shimbashi", "浜松町": "Hamamatsucho", "田町": "Tamachi",
	"大崎": "Osaki", "五反田": "Gotanda", "恵比寿": "Ebisu", "原宿": "Harajuku", "代々木": "Yoyogi",
	"高田馬場": "Takadanobaba", "目白": "Mejiro", "大塚": "Otsuka", "巣鴨": "Sugamo", "駒込": "Komagome",
	"田端": "Tabata", "西日暮里": "Nishi-Nippori", "日暮里": "Nippori", "鶯谷": "Uguisudani",
	"御茶ノ水": "Ochanomizu", "飯田橋": "Iidabashi", "市ヶ谷": "Ichigaya", "四ツ谷": "Yotsuya",
	"信濃町": "Shinanomachi", "千駄ケ谷": "Sendagaya", "大久保": "Okubo", "新大久保": "Shin-Okubo",
	"中野坂上": "Nakano-sakaue", "荻窪": "Ogikubo", "西荻窪": "Nishi-Ogikubo", "吉祥寺": "Kichijoji",
	"三鷹": "Mitaka", "武蔵境": "Musashisakai", "東小金井": "Higashikoganei", "武蔵小金井": "Musashikoganei",
	"国分寺": "Kokubunji", "西国分寺": "Nishikokubunji", "国立": "Kunitachi", "立川": "Tachikawa",
	"日野": "Hino", "豊田": "Toyoda", "八王子": "Hachioji", "西八王子": "Nishi-Hachioji", "高尾": "Takao",
	"多摩": "Tama", "町田": "Machida", "調布": "Chofu", "府中": "Fuchu", "小金井": "Koganei",
	"横浜": "Yokohama", "川崎": "Kawasaki", "鎌倉": "Kamakura", "箱根": "Hakone", "成田": "Narita",

	// Surnames and Place stems
	"山本": "Yamamoto", "山元": "Yamamoto", "松岡": "Matsuoka", "北村": "Kitamura",
	"山脇": "Yamawaki", "田中": "Tanaka", "佐藤": "Sato", "鈴木": "Suzuki", "高橋": "Takahashi",
	"渡辺": "Watanabe", "渡邊": "Watanabe", "伊藤": "Ito", "中村": "Nakamura", "小林": "Kobayashi",
	"加藤": "Kato", "吉田": "Yoshida", "山田": "Yamada", "佐々木": "Sasaki", "山口": "Yamaguchi",
	"斉藤": "Saito", "斎藤": "Saito", "松本": "Matsumoto", "井上": "Inoue", "木村": "Kimura",
	"林": "Hayashi", "清水": "Shimizu", "山崎": "Yamazaki", "森": "Mori", "阿部": "Abe",
	"池田": "Ikeda", "橋本": "Hashimoto", "山下": "Yamashita", "石川": "Ishikawa", "中島": "Nakajima",
	"前田": "Maeda", "藤田": "Fujita", "小川": "Ogawa", "後藤": "Goto", "岡田": "Okada",
	"長谷川": "Hasegawa", "村上": "Murakami", "近藤": "Kondo", "石井": "Ishii", "坂本": "Sakamoto",
	"遠藤": "Endo", "青木": "Aoki", "藤井": "Fujii", "西村": "Nishimura", "福田": "Fukuda",
	"太田": "Ota", "三浦": "Miura", "藤原": "Fujiwara", "岡本": "Okamoto", "松田": "Matsuda",
	"中川": "Nakagawa", "原田": "Harada", "小野": "Ono", "田村": "Tamura", "竹内": "Takeuchi",
	"金子": "Kaneko", "和田": "Wada", "中山": "Nakayama", "石田": "Ishida", "上田": "Ueda",
	"森田": "Morita", "原": "Hara", "柴田": "Shibata", "酒井": "Sakai", "工藤": "Kudo",
	"横山": "Yokoyama", "宮本": "Miyamoto", "内田": "Uchida", "高木": "Takagi", "安藤": "Ando",
	"島田": "Shimada", "谷口": "Taniguchi", "高田": "Takada", "丸山": "Maruyama", "今井": "Imai",
	"小島": "Kojima", "河野": "Kawano", "藤本": "Fujimoto", "武田": "Takeda", "村田": "Murata",
	"杉山": "Sugiyama", "増田": "Masuda", "菅原": "Sugawara", "平野": "Hirano",
	"千葉": "Chiba", "久保": "Kubo", "松井": "Matsui", "岩崎": "Iwasaki", "桜井": "Sakurai",
	"木下": "Kinoshita", "野口": "Noguchi", "松尾": "Matsuo", "菊地": "Kikuchi", "野村": "Nomura",
	"新井": "Arai", "牧田": "Makita", "桑原": "Kuwahara", "石川島": "Ishikawajima", "石野": "Ishino",
	"都倉": "Tokura", "北山": "Kitayama", "五木田": "Gokita", "永富": "Nagatomi", "深沢": "Fukazawa",
	"ふかざわ": "Fukazawa", "みずのや": "Mizunoya", "みずもと": "Mizumoto", "さいきょう": "Saikyo",
	"まさき": "Masaki", "岩田": "Iwata", "杏林": "Kyorin", "豊島": "Toshima", "浅草寺": "Sensoji",
	"聖路加": "St. Luke's", "慈恵": "Jikei", "順天堂": "Juntendo", "慶應": "Keio",

	// Descriptive and medical
	"内視鏡": "Endoscopy", "消化器": "Gastroenterology", "皮フ科": "Dermatology", "皮膚科": "Dermatology",
	"形成外科": "Plastic Surgery", "整形外科": "Orthopedic", "眼科": "Eye", "歯科": "Dental",
	"小児科": "Pediatric", "耳鼻咽喉科": "ENT", "耳鼻科": "ENT", "産婦人科": "Maternity",
	"心療内科": "Psychosomatic", "循環器": "Cardiovascular", "心臓": "Heart", "痛み": "Pain",
	"睡眠": "Sleep", "デイセンター": "Day Center",
	"駅前": "Station-Mae", "東": "East", "西": "West", "南": "South", "北": "North",
	"桜": "Sakura", "さくら": "Sakura", "みどり": "Midori", "緑": "Midori", "光": "Hikari",
	"あおば": "Aoba", "青葉": "Aoba", "平和": "Heiwa", "希望": "Kibo", "愛": "Ai",
	"寿": "Kotobuki", "富士": "Fuji", "太陽": "Taiyo", "大和": "Yamato", "朝日": "Asahi",
	"つばさ": "Tsubasa", "こばと": "Kobato", "ひまわり": "Himawari", "若葉": "Wakaba",
}

var katakanaMap = map[string]string{
	"ア": "A", "イ": "I", "ウ": "U", "エ": "E", "オ": "O",
	"カ": "Ka", "キ": "Ki", "ク": "Ku", "ケ": "Ke", "コ": "Ko",
	"サ": "Sa", "シ": "Shi", "ス": "Su", "セ": "Se", "ソ": "So",
	"タ": "Ta", "チ": "Chi", "ツ": "Tsu", "テ": "Te", "ト": "To",
	"ナ": "Na", "ニ": "Ni", "ヌ": "Nu", "ネ": "Ne", "ノ": "No",
	"ハ": "Ha", "ヒ": "Hi", "フ": "Fu", "ヘ": "He", "ホ": "Ho",
	"マ": "Ma", "ミ": "Mi", "ム": "Mu", "メ": "Me", "モ": "Mo",
	"ヤ": "Ya", "ユ": "Yu", "ヨ": "Yo",
	"ラ": "Ra", "リ": "Ri", "ル": "Ru", "レ": "Re", "ロ": "Ro",
	"ワ": "Wa", "ヲ": "Wo", "ン": "n",
	"ガ": "Ga", "ギ": "Gi", "グ": "Gu", "ゲ": "Ge", "ゴ": "Go",
	"ザ": "Za", "ジ": "Ji", "ズ": "Zu", "ゼ": "Ze", "ゾ": "Zo",
	"ダ": "Da", "ヂ": "Ji", "ヅ": "Zu", "デ": "De", "ド": "Do",
	"バ": "Ba", "ビ": "Bi", "ブ": "Bu", "ベ": "Be", "ボ": "Bo",
	"パ": "Pa", "ピ": "Pi", "プ": "Pu", "ペ": "Pe", "ポ": "Po",
	"キャ": "Kya", "キュ": "Kyu", "キョ": "Kyo",
	"シャ": "Sha", "シュ": "Shu", "ショ": "Sho",
	"チャ": "Cha", "チュ": "Chu", "チョ": "Cho",
	"ニャ": "Nya", "ニュ": "Nyu", "ニョ": "Nyo",
	"ヒャ": "Hya", "ヒュ": "Hyu", "ヒョ": "Hyo",
	"ミャ": "Mya", "ミュ": "Myu", "ミョ": "Myo",
	"リャ": "Rya", "リュ": "Ryu", "リョ": "Ryo",
	"ギャ": "Gya", "ギュ": "Gyu", "ギョ": "Gyo",
	"ジャ": "Ja", "ジュ": "Ju", "ジョ": "Jo",
	"ビャ": "Bya", "ビュ": "Byu", "ビョ": "Byo",
	"ピャ": "Pya", "ピュ": "Pyu", "ピョ": "Pyo",
	"ファ": "Fa", "フィ": "Fi", "フェ": "Fe", "フォ": "Fo",
	"ティ": "Ti", "ディ": "Di", "チェ": "Che", "ジェ": "Je",
	"ウィ": "Wi", "ウェ": "We", "ウォ": "Wo",
}

func transliterateKatakana(s string) string {
	// Common English loan words
	loanWords := map[string]string{
		"ファミリー": "Family", "ハート": "Heart", "スマイル": "Smile", "ケア": "Care",
		"メディカル": "Medical", "セントラル": "Central", "ライフ": "Life", "サン": "Sun",
		"グリーン": "Green", "アイ": "Eye", "デンタル": "Dental", "キッズ": "Kids",
		"レディース": "Ladies", "アリス": "Alice", "サトウ": "Sato", "グレゴリー": "Gregory",
		"マツモトキヨシ": "Matsumoto Kiyoshi", "スギ": "Sugi", "ツルハ": "Tsuruha", "サンドラッグ": "Sundrug",
		"ウエルシア": "Welcia", "ココカラファイン": "Cocokara Fine", "セブンイレブン": "7-Eleven",
		"ローソン": "Lawson", "ファミリーマート": "FamilyMart", "ミニストップ": "Ministop",
	}
	if w, ok := loanWords[s]; ok {
		return w
	}

	var out strings.Builder
	runes := []rune(s)
	for i := 0; i < len(runes); i++ {
		// Long vowel marker ー
		if runes[i] == 'ー' {
			continue
		}
		// Small tsu ッ (doubles next consonant)
		if runes[i] == 'ッ' {
			if i+1 < len(runes) {
				next := string(runes[i+1])
				if rom, ok := katakanaMap[next]; ok && len(rom) > 0 {
					out.WriteByte(strings.ToLower(rom)[0])
				}
			}
			continue
		}
		// 2-character combo e.g. キャ, シャ
		if i+1 < len(runes) {
			pair := string(runes[i : i+2])
			if rom, ok := katakanaMap[pair]; ok {
				out.WriteString(rom)
				i++
				continue
			}
		}
		// 1-character
		single := string(runes[i])
		if rom, ok := katakanaMap[single]; ok {
			out.WriteString(rom)
		}
	}
	res := out.String()
	if len(res) > 0 {
		return strings.ToUpper(res[:1]) + strings.ToLower(res[1:])
	}
	return res
}

func transliterateHiragana(s string) string {
	// Convert hiragana to katakana then transliterate
	var kata strings.Builder
	for _, r := range s {
		if r >= 0x3041 && r <= 0x3096 {
			kata.WriteRune(r + 0x60)
		} else {
			kata.WriteRune(r)
		}
	}
	return transliterateKatakana(kata.String())
}
