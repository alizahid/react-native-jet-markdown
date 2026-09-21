import { describe, expect, mock, test } from "bun:test";

// serializeStyles only uses processColor from react-native; the real module
// is Flow-typed native source that bun cannot parse.
mock.module("react-native", () => ({
  StyleSheet: {
    flatten: (style: unknown) => {
      if (Array.isArray(style)) {
        return Object.assign({}, ...style.filter(Boolean));
      }
      return style ?? {};
    },
  },
  processColor: (value: unknown): number | object | null => {
    if (typeof value === "number") {
      return value;
    }
    if (typeof value === "object" && value !== null) {
      // Platform colors pass through processColor as their descriptors.
      return value;
    }
    if (value === "red") {
      return 0xff_ff_00_00 | 0;
    }
    if (value === "blue") {
      return 0xff_00_00_ff | 0;
    }
    if (typeof value === "string" && value.startsWith("#")) {
      return 0xff_00_00_00 | Number.parseInt(value.slice(1), 16) | 0;
    }
    return null;
  },
}));

const { serializeStyles } = await import("../serializeStyles");

function parse(json: string): Record<string, any> {
  return JSON.parse(json);
}

describe("serializeStyles", () => {
  test("platform color descriptors pass through as objects", () => {
    const out = parse(
      serializeStyles(
        {
          link: { color: { semantic: ["linkColor"] } as never },
          paragraph: {
            color: { resource_paths: ["?attr/colorPrimary"] } as never,
          },
        },
        undefined
      )
    );
    expect(out.link.color).toEqual({ semantic: ["linkColor"] });
    expect(out.paragraph.color).toEqual({
      resource_paths: ["?attr/colorPrimary"],
    });
  });

  test("empty input produces empty object", () => {
    expect(serializeStyles(undefined, undefined)).toBe("{}");
  });

  test("video styles preserve image options without inheriting image overrides", () => {
    const out = parse(
      serializeStyles(
        {
          image: { height: 120, backgroundColor: "red" },
          video: {
            height: 240,
            maxHeight: 180,
            borderRadius: 16,
            backgroundColor: {
              semantic: ["secondarySystemBackgroundColor"],
            } as never,
          },
        },
        undefined
      )
    );
    expect(out.image).toEqual({
      height: 120,
      backgroundColor: 0xff_ff_00_00 | 0,
    });
    expect(out.video).toEqual({
      height: 240,
      maxHeight: 180,
      borderRadius: 16,
      backgroundColor: { semantic: ["secondarySystemBackgroundColor"] },
    });
    expect(serializeStyles({ video: {} }, undefined)).toBe("{}");
  });

  test("main padding shorthand expands into sides", () => {
    const out = parse(
      serializeStyles(undefined, { padding: 16, paddingTop: 4, gap: 8 })
    );
    expect(out.main).toEqual({
      paddingLeft: 16,
      paddingRight: 16,
      paddingTop: 4,
      paddingBottom: 16,
      gap: 8,
    });
  });

  test("colors are processed to ints", () => {
    const out = parse(
      serializeStyles({ paragraph: { color: "red" } }, undefined)
    );
    expect(out.paragraph.color).toBe(0xff_ff_00_00 | 0);
  });

  test("fontWeight normalizes to string", () => {
    const out = parse(
      serializeStyles(
        { bold: { fontWeight: 700 }, italic: { fontWeight: "bold" } },
        undefined
      )
    );
    expect(out.bold.fontWeight).toBe("700");
    expect(out.italic.fontWeight).toBe("bold");
  });

  test("heading levels serialize independently", () => {
    const out = parse(
      serializeStyles(
        { headings: { h1: { fontSize: 40 }, h3: { color: "blue" } } },
        undefined
      )
    );
    expect(out.h1).toEqual({ fontSize: 40 });
    expect(out.h3).toEqual({ color: 0xff_00_00_ff | 0 });
    expect(out.h2).toBeUndefined();
  });

  test("mention variants are ordered longest pattern first", () => {
    const out = parse(
      serializeStyles(
        {
          mention: {
            color: "red",
            variants: {
              "^u:": { color: "blue" },
              "^users://": { color: "red" },
              "^ch:": { color: "blue" },
            },
          },
        },
        undefined
      )
    );
    expect(
      out.mention.variants.map((pair: [string, unknown]) => pair[0])
    ).toEqual(["^users://", "^ch:", "^u:"]);
  });

  test("border shorthand expands per side, sides win", () => {
    const out = parse(
      serializeStyles(
        {
          blockQuote: {
            borderColor: "red",
            borderWidth: 2,
            borderLeftWidth: 4,
          },
        },
        undefined
      )
    );
    expect(out.blockQuote.borderLeftWidth).toBe(4);
    expect(out.blockQuote.borderRightWidth).toBe(2);
    expect(out.blockQuote.borderTopColor).toBe(0xff_ff_00_00 | 0);
  });

  test("main text keys become the base section, layout keys stay in main", () => {
    const out = parse(
      serializeStyles(undefined, {
        padding: 16,
        gap: 8,
        fontFamily: "Georgia",
        fontSize: 17,
        color: "red",
      })
    );
    expect(out.main.paddingLeft).toBe(16);
    expect(out.main.gap).toBe(8);
    expect(out.main.fontFamily).toBeUndefined();
    expect(out.base).toEqual({
      fontSize: 17,
      fontFamily: "Georgia",
      color: 0xff_ff_00_00 | 0,
    });
  });

  test("lineHeight rides in text styles and the base section", () => {
    const out = parse(
      serializeStyles(
        { paragraph: { lineHeight: 26 }, headings: { h1: { lineHeight: 40 } } },
        { lineHeight: 24 }
      )
    );
    expect(out.base.lineHeight).toBe(24);
    expect(out.paragraph.lineHeight).toBe(26);
    expect(out.h1.lineHeight).toBe(40);
  });

  test("styles gap serializes top-level, main gap stays in main", () => {
    const out = parse(serializeStyles({ gap: 12 }, { gap: 8 }));
    expect(out.gap).toBe(12);
    expect(out.main.gap).toBe(8);
  });

  test("output is stable for identical input", () => {
    const styles = { paragraph: { fontSize: 15 }, bold: { color: "red" } };
    expect(serializeStyles(styles, { gap: 4 })).toBe(
      serializeStyles(styles, { gap: 4 })
    );
  });

  test("undefined values are omitted", () => {
    const out = parse(
      serializeStyles(
        { paragraph: { fontSize: undefined, color: undefined } },
        undefined
      )
    );
    expect(out.paragraph).toBeUndefined();
  });

  test("axis padding expands with side > axis > padding precedence", () => {
    const out = parse(
      serializeStyles(undefined, {
        padding: 16,
        paddingHorizontal: 8,
        paddingTop: 2,
      })
    );
    expect(out.main).toEqual({
      paddingLeft: 8,
      paddingRight: 8,
      paddingTop: 2,
      paddingBottom: 16,
    });
  });

  test("inlineCode axis padding expands to left/right", () => {
    const out = parse(
      serializeStyles({ inlineCode: { paddingHorizontal: 4 } }, undefined)
    );
    expect(out.inlineCode).toEqual({ paddingLeft: 4, paddingRight: 4 });
  });

  test("table header/body rows and header cell serialize", () => {
    const out = parse(
      serializeStyles(
        {
          tableHeaderRow: { backgroundColor: "red" },
          tableBodyRow: { backgroundColor: "blue" },
          tableHeaderCell: { color: "red", paddingVertical: 10 },
        },
        undefined
      )
    );
    expect(out.tableHeaderRow.backgroundColor).toBe(0xff_ff_00_00 | 0);
    expect(out.tableBodyRow.backgroundColor).toBe(0xff_00_00_ff | 0);
    expect(out.tableHeaderCell.color).toBe(0xff_ff_00_00 | 0);
    expect(out.tableHeaderCell.paddingTop).toBe(10);
    expect(out.tableHeaderCell.paddingBottom).toBe(10);
    expect(out.tableHeaderCell.paddingLeft).toBeUndefined();
  });

  test("chip keys serialize for link, mention, and inline code", () => {
    const out = parse(
      serializeStyles(
        {
          link: { borderRadius: 4, borderCurve: "continuous" },
          mention: { borderRadius: 6 },
          inlineCode: { borderRadius: 3, borderCurve: "continuous" },
        },
        undefined
      )
    );
    expect(out.link).toEqual({ borderRadius: 4, borderCurve: "continuous" });
    expect(out.mention.borderRadius).toBe(6);
    expect(out.inlineCode.borderCurve).toBe("continuous");
  });
});
