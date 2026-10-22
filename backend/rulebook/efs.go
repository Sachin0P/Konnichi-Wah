package rulebook

import (
	"embed"
)

// The rulebook lives at the module root (not under internal/) so that the
// Android build can copy the same etiquette.json into the app's assets. That
// gives us one source of truth for the online and offline rules.
//
// The go:embed directive below embeds the file into our Go binaries, so the
// seed command works without needing the file on disk at runtime.
//
//go:embed "etiquette.json"
var Files embed.FS
