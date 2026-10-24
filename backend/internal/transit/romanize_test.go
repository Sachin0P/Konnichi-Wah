package transit

import "testing"

func TestCleanStationName(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"神田(東京都)", "神田"},
		{"新宿(ＪＲ・小田急・京王)", "新宿"},
		{"なんば(南海)", "なんば"},
		{"大崎 行き", "大崎"},
		{"JR京都駅", "京都"},
		{"浅草(東武・都営・メトロ)", "浅草"},
		{"高尾方面", "高尾"},
		{"押上〈スカイツリー前〉", "押上"},
		{"東京メトロ銀座駅", "銀座"},
	}

	for _, tt := range tests {
		got := CleanStationName(tt.input)
		if got != tt.want {
			t.Errorf("CleanStationName(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}

func TestRomanizeStation(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"神田(東京都)", "Kanda"},
		{"大崎", "Osaki"},
		{"津田沼", "Tsudanuma"},
		{"高尾", "Takao"},
		{"新大久保", "Shin-Okubo"},
		{"高輪ゲートウェイ", "Takanawa Gateway"},
		{"東大前", "Todai-mae"},
		{"Shinjuku", "Shinjuku"},
	}

	for _, tt := range tests {
		got := RomanizeStation(tt.input)
		if got != tt.want {
			t.Errorf("RomanizeStation(%q) = %q, want %q", tt.input, got, tt.want)
		}
	}
}
