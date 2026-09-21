import {
  type ColorValue,
  processColor,
  type StyleProp,
  StyleSheet,
} from "react-native";

import type {
  MarkdownContainerStyle,
  MarkdownLayoutStyle,
  MarkdownStyles,
  MarkdownTextStyle,
} from "./types";

type Serialized = Record<string, unknown>;

const MAIN_STYLE_KEYS = [
  "backgroundColor",
  "padding",
  "paddingHorizontal",
  "paddingVertical",
  "paddingLeft",
  "paddingRight",
  "paddingTop",
  "paddingBottom",
  "gap",
  // Base text styles: cascade into every text element via stylesJson.
  "fontSize",
  "fontWeight",
  "fontFamily",
  "lineHeight",
  "color",
  "fontVariant",
  "textDecorationColor",
  "textDecorationLine",
  "textDecorationStyle",
] as const;

/**
 * Splits the container `style` prop: content-affecting keys are measured
 * natively so they travel in stylesJson (`main`); anything else passes
 * through to the host view.
 */
export function splitContainerStyle(style: StyleProp<MarkdownContainerStyle>): {
  hostStyle: Record<string, unknown>;
  main: MainStyle;
} {
  const flattened = StyleSheet.flatten(style) ?? {};
  const main: MainStyle = {};
  const hostStyle: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(flattened)) {
    if ((MAIN_STYLE_KEYS as readonly string[]).includes(key)) {
      main[key as keyof MainStyle] = value as never;
    } else {
      hostStyle[key] = value;
    }
  }
  return { hostStyle, main };
}

/**
 * Main container style extracted from the `style` prop; these keys affect
 * native content layout (and measurement), so they ride along in stylesJson.
 * Text keys become the `base` section: the root of the text-style cascade.
 */
export interface MainStyle extends MarkdownTextStyle {
  backgroundColor?: ColorValue;
  gap?: number;
  padding?: number;
  paddingBottom?: number;
  paddingHorizontal?: number;
  paddingLeft?: number;
  paddingRight?: number;
  paddingTop?: number;
  paddingVertical?: number;
}

function put(out: Serialized, key: string, value: unknown): void {
  if (value !== undefined && value !== null) {
    out[key] = value;
  }
}

function putColor(
  out: Serialized,
  key: string,
  value: ColorValue | undefined
): void {
  if (value === undefined || value === null) {
    return;
  }
  const processed = processColor(value);
  if (typeof processed === "number") {
    // The Int32 JSON contract wants signed 32-bit; iOS processColor returns
    // unsigned.
    out[key] = processed | 0;
  } else if (typeof processed === "object" && processed !== null) {
    // Platform colors (PlatformColor / DynamicColorIOS): processColor
    // returns a JSON-serializable descriptor ({semantic}/{dynamic} on iOS,
    // {resource_paths} on Android) that the native style parser resolves.
    out[key] = processed;
  }
}

// Expands `padding` shorthands into explicit sides so native code never
// sees shorthand. Precedence: side > axis (Horizontal/Vertical) > padding.
function putPadding(
  out: Serialized,
  style: {
    padding?: number;
    paddingBottom?: number;
    paddingHorizontal?: number;
    paddingLeft?: number;
    paddingRight?: number;
    paddingTop?: number;
    paddingVertical?: number;
  },
  sides: ReadonlyArray<"Left" | "Right" | "Top" | "Bottom">
): void {
  const axis = {
    Bottom: style.paddingVertical,
    Left: style.paddingHorizontal,
    Right: style.paddingHorizontal,
    Top: style.paddingVertical,
  } as const;
  for (const side of sides) {
    put(
      out,
      `padding${side}`,
      style[`padding${side}`] ?? axis[side] ?? style.padding
    );
  }
}

function serializeText(
  style: MarkdownTextStyle | undefined
): Serialized | undefined {
  if (style == null) {
    return;
  }
  const out: Serialized = {};
  put(out, "fontSize", style.fontSize);
  if (style.fontWeight !== undefined) {
    out.fontWeight = String(style.fontWeight);
  }
  put(out, "fontFamily", style.fontFamily);
  put(out, "lineHeight", style.lineHeight);
  putColor(out, "backgroundColor", style.backgroundColor);
  putColor(out, "color", style.color);
  put(out, "fontVariant", style.fontVariant);
  putColor(out, "textDecorationColor", style.textDecorationColor);
  put(out, "textDecorationLine", style.textDecorationLine);
  put(out, "textDecorationStyle", style.textDecorationStyle);
  return Object.keys(out).length > 0 ? out : undefined;
}

function serializeLayout(
  style: MarkdownLayoutStyle | undefined
): Serialized | undefined {
  if (style == null) {
    return;
  }
  const out: Serialized = {};
  putColor(out, "backgroundColor", style.backgroundColor);
  putPadding(out, style, ["Left", "Right", "Top", "Bottom"]);
  put(out, "borderRadius", style.borderRadius);
  put(out, "borderCurve", style.borderCurve);
  for (const side of ["Left", "Right", "Top", "Bottom"] as const) {
    putColor(
      out,
      `border${side}Color`,
      style[`border${side}Color`] ?? style.borderColor
    );
    put(
      out,
      `border${side}Width`,
      style[`border${side}Width`] ?? style.borderWidth
    );
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

function merge(
  ...parts: Array<Serialized | undefined>
): Serialized | undefined {
  const defined = parts.filter(
    (part): part is Serialized => part !== undefined
  );
  if (defined.length === 0) {
    return;
  }
  return Object.assign({}, ...defined);
}

/**
 * Serializes the styles into the stable JSON string handed to native code.
 * Colors become processed ARGB ints, padding shorthands are expanded, and
 * mention variants become a longest-pattern-first ordered array.
 */
// biome-ignore lint/complexity/noExcessiveCognitiveComplexity: go away
export function serializeStyles(
  styles: MarkdownStyles | undefined,
  main: MainStyle | undefined
): string {
  const out: Serialized = {};

  if (main != null) {
    const section: Serialized = {};
    putColor(section, "backgroundColor", main.backgroundColor);
    putPadding(section, main, ["Left", "Right", "Top", "Bottom"]);
    put(section, "gap", main.gap);
    if (Object.keys(section).length > 0) {
      out.main = section;
    }
    // Text keys cascade into every text element as the `base` style. The
    // container's backgroundColor must not ride along, or every text run
    // would inherit it as a highlight.
    const { backgroundColor: _containerBackground, ...mainText } = main;
    put(out, "base", serializeText(mainText));
  }

  if (styles != null) {
    for (const level of ["h1", "h2", "h3", "h4", "h5", "h6"] as const) {
      put(out, level, serializeText(styles.headings?.[level]));
    }
    put(out, "paragraph", serializeText(styles.paragraph));

    for (const key of ["image", "video"] as const) {
      const style = styles[key];
      if (style == null) {
        continue;
      }
      const media: Serialized = {};
      put(media, "borderRadius", style.borderRadius);
      putColor(media, "backgroundColor", style.backgroundColor);
      put(media, "height", style.height);
      put(media, "maxHeight", style.maxHeight);
      if (Object.keys(media).length > 0) {
        out[key] = media;
      }
    }

    if (styles.table != null) {
      const table = serializeLayout(styles.table) ?? {};
      put(table, "minColumnWidth", styles.table.minColumnWidth);
      put(table, "maxColumnWidth", styles.table.maxColumnWidth);
      if (Object.keys(table).length > 0) {
        out.table = table;
      }
    }
    put(out, "tableRow", serializeLayout(styles.tableRow));
    put(out, "tableHeaderRow", serializeLayout(styles.tableHeaderRow));
    put(out, "tableBodyRow", serializeLayout(styles.tableBodyRow));
    if (styles.tableHeaderCell != null) {
      const headerCell = serializeText(styles.tableHeaderCell) ?? {};
      putPadding(headerCell, styles.tableHeaderCell, [
        "Left",
        "Right",
        "Top",
        "Bottom",
      ]);
      if (Object.keys(headerCell).length > 0) {
        out.tableHeaderCell = headerCell;
      }
    }
    if (styles.tableCell != null) {
      const cell = serializeText(styles.tableCell) ?? {};
      putPadding(cell, styles.tableCell, ["Left", "Right", "Top", "Bottom"]);
      if (Object.keys(cell).length > 0) {
        out.tableCell = cell;
      }
    }

    if (styles.spoiler != null) {
      const spoiler: Serialized = {};
      putColor(spoiler, "backgroundColor", styles.spoiler.backgroundColor);
      put(spoiler, "borderRadius", styles.spoiler.borderRadius);
      put(spoiler, "borderCurve", styles.spoiler.borderCurve);
      if (Object.keys(spoiler).length > 0) {
        out.spoiler = spoiler;
      }
    }

    put(out, "gap", styles.gap);

    if (styles.divider != null) {
      const divider: Serialized = {};
      putColor(divider, "color", styles.divider.color);
      put(divider, "height", styles.divider.height);
      if (Object.keys(divider).length > 0) {
        out.divider = divider;
      }
    }

    put(out, "superscript", serializeText(styles.superscript));
    put(out, "subscript", serializeText(styles.subscript));
    put(out, "bold", serializeText(styles.bold));
    put(out, "italic", serializeText(styles.italic));
    put(out, "strikethrough", serializeText(styles.strikethrough));

    if (styles.list != null) {
      const list: Serialized = {};
      put(list, "marginLeft", styles.list.marginLeft);
      if (Object.keys(list).length > 0) {
        out.list = list;
      }
    }
    if (styles.listMarker != null) {
      const marker: Serialized = {};
      put(marker, "width", styles.listMarker.width);
      put(marker, "marginLeft", styles.listMarker.marginLeft);
      putColor(marker, "color", styles.listMarker.color);
      if (Object.keys(marker).length > 0) {
        out.listMarker = marker;
      }
    }
    put(out, "listItem", serializeText(styles.listItem));

    if (styles.link != null) {
      const link = serializeText(styles.link) ?? {};
      put(link, "borderRadius", styles.link.borderRadius);
      put(link, "borderCurve", styles.link.borderCurve);
      if (Object.keys(link).length > 0) {
        out.link = link;
      }
    }

    if (styles.mention != null) {
      const { variants, ...base } = styles.mention;
      const mention = serializeText(base) ?? {};
      put(mention, "borderRadius", styles.mention.borderRadius);
      put(mention, "borderCurve", styles.mention.borderCurve);
      if (variants != null) {
        const ordered = Object.keys(variants)
          .sort((a, b) => b.length - a.length || a.localeCompare(b))
          .map((pattern) => [pattern, serializeText(variants[pattern]) ?? {}]);
        if (ordered.length > 0) {
          mention.variants = ordered;
        }
      }
      if (Object.keys(mention).length > 0) {
        out.mention = mention;
      }
    }

    if (styles.inlineCode != null) {
      const code = serializeText(styles.inlineCode) ?? {};
      putColor(code, "backgroundColor", styles.inlineCode.backgroundColor);
      put(code, "borderRadius", styles.inlineCode.borderRadius);
      put(code, "borderCurve", styles.inlineCode.borderCurve);
      putPadding(code, styles.inlineCode, ["Left", "Right"]);
      if (Object.keys(code).length > 0) {
        out.inlineCode = code;
      }
    }

    put(
      out,
      "codeBlock",
      merge(serializeText(styles.codeBlock), serializeLayout(styles.codeBlock))
    );
    put(
      out,
      "blockQuote",
      merge(
        serializeText(styles.blockQuote),
        serializeLayout(styles.blockQuote)
      )
    );
  }

  return JSON.stringify(out);
}
