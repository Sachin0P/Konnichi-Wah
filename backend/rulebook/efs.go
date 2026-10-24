package rulebook

import (
	"embed"
)

// The rulebook lives at the module root (not under internal/) so that the
// Android build can copy the same files into the app's assets. That gives us
// one source of truth for the online and offline content:
//
//   - etiquette.json: the etiquette rules.
//   - phrases.json: hand-checked Japanese diet cards. These are shown to
//     restaurant staff, so they're never machine-translated on the fly.
//   - etiquette.hi.json and phrases.hi.json: the Hindi versions of the
//     rules and of each card's meaning, keyed by id. The English files stay
//     the source of truth; a rule or card missing here is shown in English.
//
// The go:embed directive below embeds the files into our Go binaries, so they
// work without the files on disk at runtime.
//
//go:embed "etiquette.json" "phrases.json" "etiquette.hi.json" "phrases.hi.json"
var Files embed.FS
