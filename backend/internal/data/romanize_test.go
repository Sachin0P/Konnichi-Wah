package data

import "testing"

func TestEnglishPlaceName(t *testing.T) {
	tests := []struct {
		name     string
		kind     string
		expected string
	}{
		// Hospitals and clinics
		{"山元病院", "hospital", "Yamamoto Hospital"},
		{"松岡眼科", "hospital", "Matsuoka Eye Clinic"},
		{"みずもと内視鏡　消化器内科クリニック", "clinic", "Mizumoto Endoscopy Clinic"},
		{"さいきょうクリニック", "clinic", "Saikyo Clinic"},
		{"伏見駅前 皮フ科・形成外科クリニック", "clinic", "Fushimi Station-Mae Dermatology Clinic"},
		{"北村医院", "clinic", "Kitamura Clinic"},
		{"山脇小児科", "clinic", "Yamawaki Pediatrics Clinic"},
		{"永富歯科医院", "clinic", "Nagatomi Dental Clinic"},
		{"まさき整骨院", "clinic", "Masaki Osteopathic Clinic"},
		{"東京多摩病院", "hospital", "Tokyo Tama Hospital"},
		{"医療法人社団健育会 石川島記念病院", "hospital", "Ishikawajima Memorial Hospital"},

		// Pharmacies and Police
		{"神田駅前調剤薬局", "pharmacy", "Kanda Station-Mae Pharmacy"},
		{"渋谷駅前交番", "police", "Shibuya Station-Mae Police Box"},
		{"新宿警察署", "police", "Shinjuku Police Station"},

		// Already English
		{"St. Luke's International Hospital", "hospital", "St. Luke's International Hospital"},
	}

	for _, tt := range tests {
		got := EnglishPlaceName(tt.name, tt.kind)
		if got == "" || HasJapaneseRune(got) {
			t.Errorf("EnglishPlaceName(%q, %q) = %q; still has Japanese runes", tt.name, tt.kind, got)
		}
	}
}
