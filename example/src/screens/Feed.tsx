import { FlashList } from "@shopify/flash-list";
import { useCallback, useState } from "react";
import {
  FlatList,
  Pressable as RNPressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import {
  Pressable as GHPressable,
  ScrollView as GHScrollView,
} from "react-native-gesture-handler";
import {
  JetMarkdownView,
  type MarkdownContainerStyle,
  type MarkdownStyles,
  mergeStyles,
} from "react-native-jet-markdown";

const markdownStyle: MarkdownContainerStyle = {
  fontFamily: "Satoshi",
  padding: 14,
  gap: 8,
};

// Hoisted per the list recipe: one styles object shared by every item so
// the native style config is parsed exactly once.
const styles: MarkdownStyles = mergeStyles({
  paragraph: { fontSize: 15, color: "#1F2937" },
  headings: { h2: { fontSize: 20, color: "#111827" } },
  link: { color: "#2563EB", textDecorationLine: "underline" },
  mention: {
    fontWeight: "600",
    variants: {
      "^users://": { color: "#DB2777" },
      "^channels://": { color: "#059669" },
    },
  },
  inlineCode: { backgroundColor: "#F3F4F6", color: "#BE185D" },
  spoiler: { backgroundColor: "#374151", borderRadius: 4 },
  listItem: { fontSize: 15, color: "#1F2937" },
  codeBlock: { fontSize: 12 },
  blockQuote: { color: "#4B5563" },
  tableCell: { fontSize: 13 },
});

interface Post {
  id: string;
  markdown: string;
}

const SNIPPETS = [
  "Shipped a new build today. **Huge** improvement to startup time — thanks [@ali](users://ali)!",
  "## Release notes\n\n- faster parser\n- fixed `inline code` rendering\n- better spoilers: ||surprise||",
  "Does anyone know why `useMemo` re-runs here? See [the docs](https://reactjs.org) or ask in [#help](channels://help).",
  '```ts\nconst content = useMemo(\n  () => parseMarkdown(post.body, { flavor: "gfm" }),\n  [post.body]\n);\n```\n\nA code block inside a recycled cell.',
  "> The best way to predict the future is to invent it.\n>\n> A second quoted paragraph for good measure.\n\nClassic quote via [#quotes](channels://quotes).",
  "Benchmarks so far:\n\n| Library | Parse | Layout |\n|---------|-------|--------|\n| jet-markdown | 0.4ms | 1.1ms |\n| webview-based | 12ms | 40ms |",
  "Long paragraph that wraps across multiple lines to give the recycler some variety in cell heights when scrolling quickly through the feed. It keeps going for a while so the measurement cache earns its keep.",
  "1. first\n2. second\n3. third\n\nOrdered lists inside a recycled cell.",
  "Mixed **bold**, _italic_, ~~strike~~, and a spoiler >!hidden spoiler spoiler in a card!< for testing.",
  "### Wide table\n\n| ID | Package | Version | Downloads | License | Maintainer |\n|----|---------|---------|-----------|---------|------------|\n| 1 | react-native-jet-markdown | 0.1.0 | 120,394 | MIT | @ali |\n| 2 | react-native-enriched | 1.0.0 | 88,120 | MIT | swmansion |",
  "> Block quote with a nested list:\n>\n> - quoted alpha\n> - quoted beta\n\nAnd a trailing paragraph.",
];

const POSTS: Post[] = Array.from({ length: 250 }, (_, index) => ({
  id: String(index),
  markdown: `**Post #${index + 1}**\n\n${SNIPPETS[index % SNIPPETS.length]}`,
}));

const LIST_KINDS = ["FlatList", "FlashList", "FlashList+GHScroll"] as const;
const PRESS_KINDS = ["RN Pressable", "GH Pressable"] as const;

type ListKind = (typeof LIST_KINDS)[number];
type PressKind = (typeof PRESS_KINDS)[number];

export function Feed() {
  const [lastPress, setLastPress] = useState("none yet");
  const [listKind, setListKind] = useState<ListKind>("FlatList");
  const [pressKind, setPressKind] = useState<PressKind>("RN Pressable");

  // Cards are wrapped in a Pressable: taps on plain text hit the card, taps
  // on links/mentions/spoilers are claimed by the markdown view.
  const renderItem = useCallback(
    ({ item }: { item: Post }) => {
      const Wrapper = pressKind === "GH Pressable" ? GHPressable : RNPressable;
      return (
        <Wrapper
          onPress={() => setLastPress(`card #${Number(item.id) + 1}`)}
          style={({ pressed }) => [sheet.card, pressed && sheet.cardPressed]}
        >
          <JetMarkdownView
            markdown={item.markdown}
            onLinkPress={({ url }) => setLastPress(`link ${url}`)}
            style={markdownStyle}
            styles={styles}
          />
        </Wrapper>
      );
    },
    [pressKind]
  );

  const keyExtractor = useCallback((item: Post) => item.id, []);

  return (
    <View style={sheet.container}>
      <View style={sheet.header}>
        <Text style={sheet.headerText}>last press: {lastPress}</Text>
      </View>
      <View style={sheet.controls}>
        {LIST_KINDS.map((kind) => (
          <RNPressable
            key={kind}
            onPress={() => setListKind(kind)}
            style={[sheet.chip, listKind === kind && sheet.chipActive]}
          >
            <Text
              style={listKind === kind ? sheet.chipTextActive : sheet.chipText}
            >
              {kind}
            </Text>
          </RNPressable>
        ))}
        {PRESS_KINDS.map((kind) => (
          <RNPressable
            key={kind}
            onPress={() => setPressKind(kind)}
            style={[sheet.chip, pressKind === kind && sheet.chipActive]}
          >
            <Text
              style={pressKind === kind ? sheet.chipTextActive : sheet.chipText}
            >
              {kind}
            </Text>
          </RNPressable>
        ))}
      </View>
      {listKind === "FlatList" && (
        <FlatList
          contentContainerStyle={sheet.list}
          data={POSTS}
          extraData={pressKind}
          keyExtractor={keyExtractor}
          renderItem={renderItem}
        />
      )}
      {listKind === "FlashList" && (
        <FlashList
          contentContainerStyle={sheet.list}
          data={POSTS}
          extraData={pressKind}
          keyExtractor={keyExtractor}
          renderItem={renderItem}
        />
      )}
      {listKind === "FlashList+GHScroll" && (
        <FlashList
          contentContainerStyle={sheet.list}
          data={POSTS}
          extraData={pressKind}
          keyExtractor={keyExtractor}
          renderItem={renderItem}
          renderScrollComponent={GHScrollView}
        />
      )}
    </View>
  );
}

const sheet = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#F3F4F6",
  },
  header: {
    backgroundColor: "#111827",
    padding: 8,
  },
  headerText: {
    color: "#F9FAFB",
    fontSize: 12,
  },
  controls: {
    backgroundColor: "white",
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6,
    padding: 8,
  },
  chip: {
    borderRadius: 14,
    backgroundColor: "#F3F4F6",
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  chipActive: {
    backgroundColor: "#2563EB",
  },
  chipText: {
    color: "#374151",
    fontSize: 12,
  },
  chipTextActive: {
    color: "white",
    fontSize: 12,
  },
  list: {
    padding: 12,
  },
  card: {
    backgroundColor: "white",
    borderRadius: 12,
    elevation: 1,
    marginBottom: 10,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.08,
    shadowRadius: 3,
  },
  cardPressed: {
    backgroundColor: "#EFF6FF",
  },
});
