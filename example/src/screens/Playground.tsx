import { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import {
  defaultStyles,
  JetMarkdownView,
  type MarkdownContainerStyle,
  type MarkdownStyles,
  mergeStyles,
} from "react-native-jet-markdown";

const MARKDOWN = `# Theme playground

A paragraph with **bold**, _italic_, \`inline code\`, a [link](https://example.com), and [@ali](users://ali).

> Quoted text with ~~strikethrough~~ and H~2~O.

- alpha
- beta

||A spoiler to reveal||

Lorem ipsum dolor sit amet, >!consectetur adipiscing elit. Mauris eget felis ut mi!< vehicula ||condimentum. Donec molestie erat sodales nisi|| viverra varius`;

interface Theme {
  /** Base text styles: cascade into every text element from the style prop. */
  container?: MarkdownContainerStyle;
  styles?: MarkdownStyles;
}

const THEMES: Record<string, Theme> = {
  // No styles prop at all: the viewer renders fully plain.
  None: {
    container: {
      fontFamily: "Satoshi",
      fontSize: 14,
      lineHeight: 20,
    },
  },
  Default: {
    container: {
      fontFamily: "Satoshi",
      fontSize: 14,
      lineHeight: 20,
    },
    styles: defaultStyles,
  },
  Serif: {
    // fontFamily/fontSize/color cascade into paragraphs, lists, quotes,
    // and headings — only deviations live in `styles`.
    container: {
      fontFamily: "Zodiak",
      fontSize: 14,
      lineHeight: 20,
      color: "#44403C",
    },
    styles: mergeStyles({
      headings: {
        h1: { color: "#7C2D12" },
      },
      blockQuote: {
        color: "#78716C",
        borderLeftColor: "#EA580C",
        borderLeftWidth: 4,
        backgroundColor: "#FFF7ED",
        padding: 12,
        borderRadius: 8,
      },
      link: { color: "#C2410C", textDecorationLine: "underline" },
      mention: { color: "#9333EA", fontWeight: "700" },
      spoiler: { backgroundColor: "#7C2D12", borderRadius: 8 },
    }),
  },
  Compact: {
    container: {
      fontFamily: "Satoshi",
      fontSize: 12,
      lineHeight: 16,
      color: "#111",
    },
    styles: mergeStyles({
      headings: { h1: { fontSize: 22 } },
      blockQuote: { color: "#666" },
      inlineCode: {
        fontSize: 12,
        backgroundColor: "#EEF2FF",
        color: "#4338CA",
      },
      spoiler: { backgroundColor: "#111827", borderRadius: 2 },
    }),
  },
};

// undefined = no container gap: the styles prop (e.g. defaultStyles.gap)
// drives spacing, or 0 when unstyled.
const GAPS = [undefined, 6, 12, 20];

export function Playground() {
  const [theme, setTheme] = useState<keyof typeof THEMES>("Default");
  const [gapIndex, setGapIndex] = useState(0);

  return (
    <View style={sheet.container}>
      <View style={sheet.controls}>
        {Object.keys(THEMES).map((name) => (
          <Pressable
            key={name}
            onPress={() => setTheme(name as keyof typeof THEMES)}
            style={[sheet.chip, theme === name && sheet.chipActive]}
          >
            <Text
              style={theme === name ? sheet.chipTextActive : sheet.chipText}
            >
              {name}
            </Text>
          </Pressable>
        ))}
        <Pressable
          onPress={() => setGapIndex((gapIndex + 1) % GAPS.length)}
          style={sheet.chip}
        >
          <Text style={sheet.chipText}>gap {GAPS[gapIndex] ?? "auto"}</Text>
        </Pressable>
      </View>
      <ScrollView>
        <JetMarkdownView
          markdown={MARKDOWN}
          style={[
            { padding: 16, gap: GAPS[gapIndex] },
            THEMES[theme]?.container,
          ]}
          styles={THEMES[theme]?.styles}
        />
      </ScrollView>
    </View>
  );
}

const sheet = StyleSheet.create({
  container: {
    flex: 1,
  },
  controls: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    padding: 12,
  },
  chip: {
    borderRadius: 16,
    backgroundColor: "#F3F4F6",
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  chipActive: {
    backgroundColor: "#2563EB",
  },
  chipText: {
    color: "#374151",
  },
  chipTextActive: {
    color: "white",
  },
});
