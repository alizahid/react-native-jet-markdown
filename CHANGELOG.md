# Changelog

## 1.1.0

Includes the inline video support introduced in `1.1.0-beta.0` and the styling and sizing improvements from `1.1.0-beta.1`.

### Added

- HTML video embeds: `<video src="…" poster="…"/>` and paired `<video></video>` tags. Attributes support HTML quoting, entities, and case-insensitive names. Code and escaped tags remain literal.
- Native inline playback on iOS through the optional `react-native-jet-video` integration, including posters, native controls, fullscreen, and shared player pooling.
- Tap-to-play with offscreen pausing. Scrolling back leaves the video paused until another tap, and posters stay visible until the first play.
- `styles.video` and the exported `MarkdownVideoStyle` type, with the same options as image styles: `backgroundColor`, `borderRadius`, `height`, and `maxHeight`.
- Intrinsic video sizing: a full-width, 200-point placeholder resizes to the video's natural aspect ratio after metadata loads. Sizing follows image behavior, including height overrides, maximum height, and fitting the available width without upscaling.
- Video embeds preserve surrounding text and formatting, including within lists and block quotes. Rebinding an unchanged source preserves playback and loaded dimensions.

### Compatibility

- Inline playback requires `react-native-jet-video` 1.1.0 or later and its `react-native-nitro-modules` peer. Install pods and rebuild after adding or upgrading the native dependency.
- Jet Video remains optional: iOS falls back to an **Open video** link when the adapter is absent. Android renders video links; the web fallback continues to display raw markdown.
- Videos inside tables and spoilers render as links. Additional HTML video attributes and nested `<source>` elements are not interpreted.
- No breaking changes from 1.0.0.

## 1.0.0

First stable release of the native Fabric markdown renderer, with per-element styles, self-sizing layout, images, tables, spoilers, mentions, and shared parse/layout caches. Android support is experimental.
