import type { MarkdownStyles } from "./types";

/**
 * The classic markdown look. The viewer renders fully plain when `styles`
 * is omitted — pass `defaultStyles` for this look as-is, or
 * `mergeStyles({...})` to override parts of it. Tune freely: this object is
 * the single source of truth for the default appearance.
 *
 * Only semantics stay native and are not represented here: bold/italic/
 * strikethrough runs, the monospace font family for code, list markers,
 * table header-row bold, and the spoiler cover fallback.
 */
export const defaultStyles: MarkdownStyles = {
  blockQuote: {
    borderLeftColor: "rgba(0, 0, 0, 0.2)",
    borderLeftWidth: 3,
    paddingLeft: 12,
  },
  codeBlock: {
    backgroundColor: "rgba(0, 0, 0, 0.08)",
    borderRadius: 6,
    fontSize: 14,
    padding: 12,
  },
  divider: {
    color: "rgba(0, 0, 0, 0.13)",
    height: 1,
  },
  gap: 12,
  headings: {
    h1: { fontSize: 32, fontWeight: "700" },
    h2: { fontSize: 26, fontWeight: "700" },
    h3: { fontSize: 22, fontWeight: "700" },
    h4: { fontSize: 18, fontWeight: "700" },
    h5: { fontSize: 16, fontWeight: "700" },
    h6: { fontSize: 14, fontWeight: "700" },
  },
  image: {
    backgroundColor: "rgba(0, 0, 0, 0.08)",
  },
  video: {
    backgroundColor: "rgba(0, 0, 0, 0.08)",
  },
  inlineCode: {
    backgroundColor: "rgba(0, 0, 0, 0.08)",
  },
  link: {
    color: "#007AFF",
  },
  listMarker: {
    width: 24,
  },
  spoiler: {
    backgroundColor: "#3F3F46",
    borderRadius: 4,
  },
  table: {
    maxColumnWidth: 320,
    minColumnWidth: 44,
  },
  tableCell: {
    padding: 8,
  },
  tableRow: {
    borderBottomColor: "rgba(0, 0, 0, 0.12)",
    borderBottomWidth: 1,
  },
};

/**
 * Deep-merges the given overrides over {@link defaultStyles}: element
 * sections merge key-by-key, heading levels merge individually, and
 * anything not overridden keeps its default. Hoist the result to module
 * scope (or memoize) like any other `styles` value.
 */
export function mergeStyles(overrides: MarkdownStyles = {}): MarkdownStyles {
  const merged: Record<string, unknown> = { ...defaultStyles };
  for (const [key, value] of Object.entries(overrides)) {
    if (value == null) {
      continue;
    }
    const base = (defaultStyles as Record<string, unknown>)[key];
    if (key === "headings") {
      const levels: Record<string, unknown> = {
        ...defaultStyles.headings,
      };
      for (const [level, style] of Object.entries(value)) {
        levels[level] = {
          ...(levels[level] as object | undefined),
          ...(style as object),
        };
      }
      merged.headings = levels;
    } else if (
      base != null &&
      typeof base === "object" &&
      typeof value === "object"
    ) {
      merged[key] = { ...base, ...value };
    } else {
      merged[key] = value;
    }
  }
  return merged as MarkdownStyles;
}
