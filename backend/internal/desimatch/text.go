package desimatch

// The reasons and cautions Score can give, in English. Each one has a Hindi
// version below; the tests check none is missing.
const (
	reasonVeganOnly      = "Vegan-only kitchen"
	reasonVegetarianOnly = "Vegetarian-only kitchen"
	reasonVegOptions     = "Has vegetarian options"
	reasonDesi           = "Indian or Nepali food"
	reasonHalal          = "Halal"
	reasonCurry          = "Curry (familiar comfort food)"
	reasonSpicy          = "Can make it properly spicy"
	cautionVegCurry      = "Ask for the vegetarian curry: most Japanese curry sauce contains meat."
	cautionJain          = "Ask for Jain food: no onion, garlic or root vegetables. Show your diet card."
	cautionHalalVeg      = "Vegetarian, so no meat issues; check that no alcohol (mirin, sake) is used in cooking."
	cautionHalalDesi     = "Many Indian and Nepali restaurants are halal, but ask before ordering meat."
	cautionPork          = "Mostly pork-based dishes (ramen broth, tonkatsu)."
	cautionBeef          = "Mostly beef dishes."
	cautionSeafood       = "Mostly fish and seafood."
	cautionDashi         = "Japanese broths and sauces often contain dashi (fish stock): show your diet card."
)

// allTexts lists every constant above, for the tests.
var allTexts = []string{
	reasonVeganOnly, reasonVegetarianOnly, reasonVegOptions, reasonDesi, reasonHalal, reasonCurry, reasonSpicy,
	cautionVegCurry, cautionJain, cautionHalalVeg, cautionHalalDesi, cautionPork, cautionBeef, cautionSeafood, cautionDashi,
}

// hindi is hand-written, like the rulebook's Hindi. Have a native speaker
// check it before relying on it.
var hindi = map[string]string{
	reasonVeganOnly:      "सिर्फ़ वीगन रसोई",
	reasonVegetarianOnly: "सिर्फ़ शाकाहारी रसोई",
	reasonVegOptions:     "शाकाहारी विकल्प मिलते हैं",
	reasonDesi:           "भारतीय या नेपाली खाना",
	reasonHalal:          "हलाल",
	reasonCurry:          "करी (जाना-पहचाना खाना)",
	reasonSpicy:          "अच्छा तीखा बना सकते हैं",
	cautionVegCurry:      "शाकाहारी करी माँगें: ज़्यादातर जापानी करी सॉस में मांस होता है।",
	cautionJain:          "जैन खाना माँगें: बिना प्याज़, लहसुन या जड़ वाली सब्ज़ियों के। अपना डाइट कार्ड दिखाएँ।",
	cautionHalalVeg:      "शाकाहारी है, इसलिए मांस की चिंता नहीं; पूछ लें कि खाना पकाने में शराब (mirin, sake) तो नहीं डाली गई।",
	cautionHalalDesi:     "कई भारतीय और नेपाली रेस्टोरेंट हलाल होते हैं, लेकिन मांस ऑर्डर करने से पहले पूछ लें।",
	cautionPork:          "ज़्यादातर पोर्क वाली डिशें (ramen का शोरबा, tonkatsu)।",
	cautionBeef:          "ज़्यादातर बीफ़ वाली डिशें।",
	cautionSeafood:       "ज़्यादातर मछली और समुद्री भोजन।",
	cautionDashi:         "जापानी शोरबों और सॉस में अक्सर dashi (मछली का स्टॉक) होता है: अपना डाइट कार्ड दिखाएँ।",
}

// In returns the match with its reasons and caution in the given language
// ("en" or "hi"). Anything without a translation stays in English.
func (m Match) In(language string) Match {
	if language != "hi" {
		return m
	}

	translate := func(s string) string {
		if h, ok := hindi[s]; ok {
			return h
		}
		return s
	}

	out := Match{Score: m.Score, Reasons: make([]string, len(m.Reasons)), Caution: translate(m.Caution)}
	for i, r := range m.Reasons {
		out.Reasons[i] = translate(r)
	}
	return out
}
