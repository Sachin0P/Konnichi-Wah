package transit

import (
	"strings"
	"unicode"
	"unicode/utf8"
)

// CleanStationName strips parenthesized company/region notes (e.g. "(東京都)", "（メトロ）"),
// company prefixes (e.g. "JR", "東急"), and direction/station suffixes ("駅", "行き", "方面").
func CleanStationName(name string) string {
	s := strings.TrimSpace(name)
	// Remove parenthesized content: ASCII (), full-width （）, brackets 【】, angle brackets 〈〉, 〔〕
	for {
		start := -1
		end := -1
		for i, r := range s {
			if r == '(' || r == '（' || r == '[' || r == '【' || r == '〈' || r == '〔' {
				start = i
			} else if (r == ')' || r == '）' || r == ']' || r == '】' || r == '〉' || r == '〕') && start != -1 {
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

	// Remove common company prefixes
	prefixes := []string{
		"東京メトロ", "都営地下鉄", "都営", "JR東日本", "JR西日本", "JR東海", "JR",
		"東急", "京王", "小田急", "京成", "東武", "西武", "京急", "相鉄",
		"阪急", "阪神", "近鉄", "南海", "京阪", "山陽",
	}
	for _, p := range prefixes {
		if strings.HasPrefix(s, p) && len(s) > len(p) {
			s = strings.TrimSpace(strings.TrimPrefix(s, p))
			break
		}
	}

	// Remove suffixes
	suffixes := []string{"駅", "行き", "方面", "行"}
	for _, suf := range suffixes {
		if strings.HasSuffix(s, suf) && len(s) > len(suf) {
			s = strings.TrimSpace(strings.TrimSuffix(s, suf))
		}
	}

	return strings.TrimSpace(s)
}

// RomanizeStation converts a Japanese station name into standard Hepburn Romaji.
func RomanizeStation(ja string) string {
	clean := CleanStationName(ja)
	if clean == "" {
		return ja
	}
	if !hasJapaneseRune(clean) {
		return clean
	}

	// Direct dictionary lookup
	if en, ok := stationDict[clean]; ok {
		return en
	}

	// Handle standard station suffixes first: -mae, -guchi, -koen, -onsen, -kaigan, -daigaku, -dori
	type suffixRule struct {
		suffixJa string
		suffixEn string
	}
	stationSuffixes := []suffixRule{
		{"大学前", "-daigaku-mae"},
		{"前", "-mae"},
		{"口", "-guchi"},
		{"公園", "-koen"},
		{"温泉", "-onsen"},
		{"海岸", "-kaigan"},
		{"通り", "-dori"},
		{"通", "-dori"},
		{"町", "-cho"},
	}
	for _, suf := range stationSuffixes {
		if strings.HasSuffix(clean, suf.suffixJa) && len(clean) > len(suf.suffixJa) {
			stem := clean[:len(clean)-len(suf.suffixJa)]
			if stemEn, ok := stationDict[stem]; ok {
				return stemEn + suf.suffixEn
			}
		}
	}

	// Handle standard directional prefixes: Shin-, Higashi-, Nishi-, Minami-, Kita-, Naka-, Moto-, Hon-
	type prefixRule struct {
		prefixJa string
		prefixEn string
	}
	dirPrefixes := []prefixRule{
		{"新", "Shin-"},
		{"東", "Higashi-"},
		{"西", "Nishi-"},
		{"南", "Minami-"},
		{"北", "Kita-"},
		{"中", "Naka-"},
		{"本", "Hon-"},
		{"元", "Moto-"},
		{"上", "Kami-"},
		{"下", "Shimo-"},
	}
	for _, p := range dirPrefixes {
		if strings.HasPrefix(clean, p.prefixJa) && len(clean) > len(p.prefixJa) {
			rest := clean[len(p.prefixJa):]
			if restEn, ok := stationDict[rest]; ok {
				return p.prefixEn + restEn
			}
		}
	}

	// Multi-character match decomposition
	var result strings.Builder
	runes := []rune(clean)
	i := 0
	for i < len(runes) {
		matched := false
		for l := 4; l >= 1; l-- {
			if i+l <= len(runes) {
				sub := string(runes[i : i+l])
				if en, found := stationDict[sub]; found {
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

		// Katakana
		r := runes[i]
		if r >= 0x30A0 && r <= 0x30FF {
			katakanaEnd := i
			for katakanaEnd < len(runes) && runes[katakanaEnd] >= 0x30A0 && runes[katakanaEnd] <= 0x30FF {
				katakanaEnd++
			}
			kataStr := string(runes[i:katakanaEnd])
			result.WriteString(transliterateStationKatakana(kataStr))
			i = katakanaEnd
			continue
		}

		// Hiragana
		if r >= 0x3040 && r <= 0x309F {
			hiraganaEnd := i
			for hiraganaEnd < len(runes) && runes[hiraganaEnd] >= 0x3040 && runes[hiraganaEnd] <= 0x309F {
				hiraganaEnd++
			}
			hiraStr := string(runes[i:hiraganaEnd])
			result.WriteString(transliterateStationHiragana(hiraStr))
			i = hiraganaEnd
			continue
		}

		if unicode.IsLetter(r) || unicode.IsDigit(r) {
			result.WriteRune(r)
		}
		i++
	}

	res := strings.TrimSpace(result.String())
	if res == "" {
		return clean
	}
	return strings.ToUpper(res[:1]) + res[1:]
}

var stationDict = map[string]string{
	// Shinkansen and Airport Terminals
	"越後湯沢": "Echigo-Yuzawa", "新横浜": "Shin-Yokohama", "新神戸": "Shin-Kobe",
	"広島": "Hiroshima", "博多": "Hakata", "仙台": "Sendai", "盛岡": "Morioka",
	"新青森": "Shin-Aomori", "新函館北斗": "Shin-Hakodate-Hokuto", "長野": "Nagano",
	"金沢": "Kanazawa", "敦賀": "Tsuruga", "成田空港": "Narita Airport",
	"羽田空港": "Haneda Airport", "関西空港": "Kansai Airport",

	// Tokyo Major Stations & Stops
	"東大": "Todai", "京大": "Kyodai", "阪大": "Handai",
	"東京": "Tokyo", "神田": "Kanda", "秋葉原": "Akihabara", "御徒町": "Okachimachi",
	"上野": "Ueno", "鶯谷": "Uguisudani", "日暮里": "Nippori", "西日暮里": "Nishi-Nippori",
	"田端": "Tabata", "駒込": "Komagome", "巣鴨": "Sugamo", "大塚": "Otsuka",
	"池袋": "Ikebukuro", "目白": "Mejiro", "高田馬場": "Takadanobaba", "新大久保": "Shin-Okubo",
	"新宿": "Shinjuku", "代々木": "Yoyogi", "原宿": "Harajuku", "渋谷": "Shibuya",
	"恵比寿": "Ebisu", "目黒": "Meguro", "五反田": "Gotanda", "大崎": "Osaki",
	"品川": "Shinagawa", "田町": "Tamachi", "浜松町": "Hamamatsucho", "新橋": "Shimbashi",
	"有楽町": "Yurakucho", "高輪ゲートウェイ": "Takanawa Gateway",

	// Chuo-Sobu Line
	"御茶ノ水": "Ochanomizu", "水道橋": "Suidobashi", "飯田橋": "Iidabashi", "市ヶ谷": "Ichigaya",
	"四ツ谷": "Yotsuya", "信濃町": "Shinanomachi", "千駄ケ谷": "Sendagaya", "大久保": "Okubo",
	"東中野": "Higashi-Nakano", "中野": "Nakano", "高円寺": "Koenji", "阿佐ケ谷": "Asagaya",
	"荻窪": "Ogikubo", "西荻窪": "Nishi-Ogikubo", "吉祥寺": "Kichijoji", "三鷹": "Mitaka",
	"武蔵境": "Musashisakai", "東小金井": "Higashi-Koganei", "武蔵小金井": "Musashi-Koganei",
	"国分寺": "Kokubunji", "西国分寺": "Nishi-Kokubunji", "国立": "Kunitachi", "立川": "Tachikawa",
	"日野": "Hino", "豊田": "Toyoda", "八王子": "Hachioji", "西八王子": "Nishi-Hachioji", "高尾": "Takao",
	"浅草橋": "Asakusabashi", "両国": "Ryogoku", "錦糸町": "Kinshicho", "亀戸": "Kameido",
	"平井": "Hirai", "新小岩": "Shin-Koiwa", "小岩": "Koiwa", "市川": "Ichikawa",
	"本八幡": "Motoyawata", "下総中山": "Shimosa-Nakayama", "西船橋": "Nishi-Funabashi", "船橋": "Funabashi",
	"東船橋": "Higashi-Funabashi", "津田沼": "Tsudanuma", "幕張本郷": "Makuhari-Hongo", "幕張": "Makuhari",
	"新検見川": "Shin-Kemigawa", "稲毛": "Inage", "西千葉": "Nishi-Chiba", "千葉": "Chiba",

	// Tokyo Metro & Subway
	"浅草": "Asakusa", "銀座": "Ginza", "日本橋": "Nihombashi", "表参道": "Omotesando",
	"青山一丁目": "Aoyama-itchome", "赤坂見附": "Akasaka-mitsuke", "溜池山王": "Tameike-sanno",
	"虎ノ門": "Toranomon", "霞ケ関": "Kasumigaseki", "大手町": "Otemachi", "三越前": "Mitsukoshimae",
	"末広町": "Suehirocho", "湯島": "Yushima", "根津": "Nezu", "千駄木": "Sendagi",
	"町屋": "Machiya", "北千住": "Kita-Senju", "綾瀬": "Ayase", "押上": "Oshiage",
	"とうきょうスカイツリー": "Tokyo Skytree", "本所吾妻橋": "Honjo-azumabashi", "蔵前": "Kuramae",
	"東銀座": "Higashi-Ginza", "築地": "Tsukiji", "築地市場": "Tsukijishijo", "汐留": "Shiodome",
	"大門": "Daimon", "三田": "Mita", "泉岳寺": "Sengakuji", "高輪台": "Takanawadai",
	"戸越": "Togoshi", "中延": "Nakanobu", "馬込": "Magome", "西馬込": "Nishi-Magome",
	"麻布十番": "Azabu-juban", "六本木一丁目": "Roppongi-itchome", "永田町": "Nagatacho", "麹町": "Kojimachi",
	"半蔵門": "Hanzomon", "九段下": "Kudanshita", "神保町": "Jimbocho", "竹橋": "Takebashi",
	"二重橋前": "Nijubashimae", "日比谷": "Hibiya", "桜田門": "Sakuradamon",
	"月島": "Tsukishima", "豊洲": "Toyosu", "辰巳": "Tatsumi", "新木場": "Shin-Kiba",
	"清澄白河": "Kiyosumi-shirakawa", "門前仲町": "Monzen-nakacho", "木場": "Kiba", "東陽町": "Toyocho",
	"南砂町": "Minami-sunamachi", "西葛西": "Nishi-Kasai", "葛西": "Kasai", "浦安": "Urayasu",
	"南行徳": "Minami-Gyotoku", "行徳": "Gyotoku", "妙典": "Myoden", "原木中山": "Baraki-nakayama",
	"舞浜": "Maihama", "海浜幕張": "Kaihin-Makuhari",

	// Kansai Major Stations (Kyoto, Osaka, Nara, Kobe)
	"京都": "Kyoto", "山科": "Yamashina", "大津": "Otsu", "草津": "Kusatsu",
	"西大路": "Nishioji", "桂川": "Katsuragawa", "向日町": "Mukomachi", "長岡京": "Nagaokakyo",
	"山崎": "Yamazaki", "島本": "Shimamoto", "高槻": "Takatsuki", "摂津富田": "Settsu-Tonda",
	"茨木": "Ibaraki", "千里丘": "Senrioka", "岸辺": "Kishibe", "吹田": "Suita",
	"東淀川": "Higashi-Yodogawa", "新大阪": "Shin-Osaka", "大阪": "Osaka", "塚本": "Tsukamoto",
	"尼崎": "Amagasaki", "立花": "Tachibana", "甲子園口": "Koshienguchi", "西宮": "Nishinomiya",
	"さくら夙川": "Sakura-Shukugawa", "芦屋": "Ashiya", "甲南山手": "Konan-Yamate", "摂津本山": "Settsu-Motoyama",
	"住吉": "Sumiyoshi", "六甲道": "Rokkomichi", "摩耶": "Maya", "灘": "Nada", "三ノ宮": "Sannomiya",
	"元町": "Motomachi", "神戸": "Kobe", "兵庫": "Hyogo", "新長田": "Shin-Nagata",
	"梅田": "Umeda", "東梅田": "Higashi-Umeda", "西梅田": "Nishi-Umeda", "北新地": "Kitashinchi",
	"淀屋橋": "Yodoyabashi", "本町": "Hommachi", "心斎橋": "Shinsaibashi", "なんば": "Namba",
	"難波": "Namba", "大国町": "Daikokucho", "動物園前": "Dobutsuen-mae", "天王寺": "Tennoji",
	"新今宮": "Shin-Imamiya", "今宮": "Imamiya", "芦原橋": "Ashiharabashi", "大正": "Taisho",
	"弁天町": "Bentencho", "西九条": "Nishikujo", "野田": "Noda", "福島": "Fukushima",
	"天満": "Temma", "桜ノ宮": "Sakuranomiya", "京橋": "Kyobashi", "大阪城公園": "Osakajokoen",
	"森ノ宮": "Morinomiya", "玉造": "Tamatsukuri", "鶴橋": "Tsuruhashi", "桃谷": "Momodani",
	"寺田町": "Teradacho",
	"奈良": "Nara", "近鉄奈良": "Kintetsu-Nara", "新大宮": "Shin-Omiya", "西ノ京": "Nishinokyo",
	"大和西大寺": "Yamato-Saidaiji", "生駒": "Ikoma", "王寺": "Oji", "法隆寺": "Horyuji",
	"郡山": "Koriyama", "大和小泉": "Yamato-Koizumi", "天理": "Tenri", "桜井": "Sakurai",
	"稲荷": "Inari", "東福寺": "Tofukuji", "宇治": "Uji", "城陽": "Joyo", "木津": "Kizu",
	"嵯峨嵐山": "Saga-Arashiyama", "太秦": "Uzumasa", "円町": "Emmachi", "二条": "Nijo",
	"丹波口": "Tambaguchi", "出町柳": "Demachiyanagi", "清水五条": "Kiyomizu-Gojo", "祇園四条": "Gion-Shijo",
	"三条": "Sanjo", "神宮丸太町": "Jingu-Marutamachi", "烏丸御池": "Karasuma-Oike", "四条": "Shijo",
	"五条": "Gojo", "九条": "Kujo", "十条": "Jujo", "竹田": "Takeda",

	// Kanji Morphemes
	"山": "Yama", "川": "Kawa", "田": "Ta", "野": "No", "島": "Shima", "嶋": "Shima",
	"谷": "Tani", "橋": "Hashi", "町": "Machi", "村": "Mura", "里": "Sato",
	"原": "Hara", "森": "Mori", "林": "Hayashi", "木": "Ki", "石": "Ishi",
	"松": "Matsu", "竹": "Take", "梅": "Ume", "桜": "Sakura", "花": "Hana",
	"寺": "Dera", "宮": "Miya", "神": "Kami", "城": "Shiro", "館": "Kan",
	"大": "O", "小": "Ko", "高": "Taka", "長": "Naga", "中": "Naka",
	"白": "Shiro", "黒": "Kuro", "赤": "Aka", "青": "Ao", "金": "Kane",
}

var stationKatakanaMap = map[string]string{
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
	"ファ": "Fa", "フィ": "Fi", "フェ": "Fe", "フォ": "Fo",
	"ティ": "Ti", "ディ": "Di", "チェ": "Che", "ジェ": "Je",
	"ウィ": "Wi", "ウェ": "We", "ウォ": "Wo",
}

func transliterateStationKatakana(s string) string {
	loanWords := map[string]string{
		"ゲートウェイ": "Gateway", "センター": "Center", "スクエア": "Square",
		"テラス": "Terrace", "ドーム": "Dome", "タウン": "Town", "タワー": "Tower",
		"パーク": "Park", "ヒルズ": "Hills", "シティ": "City", "モール": "Mall",
		"スカイツリー": "Skytree", "リゾート": "Resort", "アイランド": "Island",
	}
	if w, ok := loanWords[s]; ok {
		return w
	}

	var out strings.Builder
	runes := []rune(s)
	for i := 0; i < len(runes); i++ {
		if runes[i] == 'ー' {
			continue
		}
		if runes[i] == 'ッ' {
			if i+1 < len(runes) {
				next := string(runes[i+1])
				if rom, ok := stationKatakanaMap[next]; ok && len(rom) > 0 {
					out.WriteByte(strings.ToLower(rom)[0])
				}
			}
			continue
		}
		if i+1 < len(runes) {
			pair := string(runes[i : i+2])
			if rom, ok := stationKatakanaMap[pair]; ok {
				out.WriteString(rom)
				i++
				continue
			}
		}
		single := string(runes[i])
		if rom, ok := stationKatakanaMap[single]; ok {
			out.WriteString(rom)
		}
	}
	res := out.String()
	if len(res) > 0 {
		return strings.ToUpper(res[:1]) + strings.ToLower(res[1:])
	}
	return res
}

func transliterateStationHiragana(s string) string {
	var kata strings.Builder
	for _, r := range s {
		if r >= 0x3041 && r <= 0x3096 {
			kata.WriteRune(r + 0x60)
		} else {
			kata.WriteRune(r)
		}
	}
	return transliterateStationKatakana(kata.String())
}
