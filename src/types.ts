import type { ColorValue, StyleProp } from "react-native";

export type FontVariant =
  | "small-caps"
  | "oldstyle-nums"
  | "lining-nums"
  | "tabular-nums"
  | "proportional-nums"
  | "stylistic-one"
  | "stylistic-two"
  | "stylistic-three"
  | "stylistic-four"
  | "stylistic-five"
  | "stylistic-six"
  | "stylistic-seven"
  | "stylistic-eight"
  | "stylistic-nine"
  | "stylistic-ten"
  | "stylistic-eleven"
  | "stylistic-twelve"
  | "stylistic-thirteen"
  | "stylistic-fourteen"
  | "stylistic-fifteen"
  | "stylistic-sixteen"
  | "stylistic-seventeen"
  | "stylistic-eighteen"
  | "stylistic-nineteen"
  | "stylistic-twenty";

export type FontWeight =
  | "normal"
  | "bold"
  | "100"
  | "200"
  | "300"
  | "400"
  | "500"
  | "600"
  | "700"
  | "800"
  | "900"
  | 100
  | 200
  | 300
  | 400
  | 500
  | 600
  | 700
  | 800
  | 900;

/**
 * Text styling shared by every text-bearing markdown element.
 */
export interface MarkdownTextStyle {
  /** Highlight behind the text run. */
  backgroundColor?: ColorValue;
  color?: ColorValue;
  fontFamily?: string;
  fontSize?: number;
  fontVariant?: FontVariant[];
  fontWeight?: FontWeight;
  /** Total line height in points, like React Native's `lineHeight`. */
  lineHeight?: number;
  textDecorationColor?: ColorValue;
  /**
   * Android renders `underline` and `line-through` natively; decoration
   * color/style on Android are drawn for links and mentions, best-effort
   * elsewhere.
   */
  textDecorationLine?:
    | "none"
    | "underline"
    | "line-through"
    | "underline line-through";
  textDecorationStyle?: "solid" | "double" | "dotted" | "dashed";
}

/**
 * Box styling shared by every block-level markdown element.
 */
export interface MarkdownLayoutStyle {
  backgroundColor?: ColorValue;
  borderBottomColor?: ColorValue;
  borderBottomWidth?: number;
  borderColor?: ColorValue;
  /** iOS only; Android always renders circular corners. */
  borderCurve?: "circular" | "continuous";
  borderLeftColor?: ColorValue;
  borderLeftWidth?: number;
  borderRadius?: number;
  borderRightColor?: ColorValue;
  borderRightWidth?: number;
  borderTopColor?: ColorValue;
  borderTopWidth?: number;
  borderWidth?: number;
  padding?: number;
  paddingBottom?: number;
  paddingHorizontal?: number;
  paddingLeft?: number;
  paddingRight?: number;
  paddingTop?: number;
  paddingVertical?: number;
}

export interface MarkdownImageStyle {
  backgroundColor?: ColorValue;
  borderRadius?: number;
  /** Fixed rendered height; wins over the image's intrinsic height. */
  height?: number;
  maxHeight?: number;
}

export interface MarkdownTableStyle extends MarkdownLayoutStyle {
  /** Upper clamp for computed column widths. Unset = natural width. */
  maxColumnWidth?: number;
  /** Lower clamp for computed column widths. Unset = natural width. */
  minColumnWidth?: number;
}

export interface MarkdownSpoilerStyle {
  backgroundColor?: ColorValue;
  /** iOS only; Android always renders circular corners. */
  borderCurve?: "circular" | "continuous";
  borderRadius?: number;
}

export interface MarkdownDividerStyle {
  color?: ColorValue;
  height?: number;
}

export interface MarkdownListStyle {
  marginLeft?: number;
}

export interface MarkdownListMarkerStyle {
  color?: ColorValue;
  marginLeft?: number;
  width?: number;
}

/**
 * Mention styling. `variants` maps a regular expression (matched against the
 * mention link's URL) to the style for that mention type. Longest pattern
 * first, first match wins:
 *
 * ```ts
 * mention: {
 *   color: 'gray',
 *   variants: {
 *     '^users://': { color: 'blue' },
 *     '^channels://': { color: 'green' },
 *   },
 * }
 * ```
 */
export interface MarkdownMentionStyle extends MarkdownTextStyle {
  /** iOS only; Android always renders circular corners. */
  borderCurve?: "circular" | "continuous";
  /** Rounds the `backgroundColor` chip behind the mention. */
  borderRadius?: number;
  variants?: Record<string, MarkdownTextStyle>;
}

/** Link styling; `backgroundColor` renders as a chip behind the link. */
export interface MarkdownLinkStyle extends MarkdownTextStyle {
  /** iOS only; Android always renders circular corners. */
  borderCurve?: "circular" | "continuous";
  /** Rounds the `backgroundColor` chip behind the link. */
  borderRadius?: number;
}

export interface MarkdownInlineCodeStyle extends MarkdownTextStyle {
  backgroundColor?: ColorValue;
  /** iOS only; Android always renders circular corners. */
  borderCurve?: "circular" | "continuous";
  borderRadius?: number;
  padding?: number;
  paddingHorizontal?: number;
  paddingLeft?: number;
  paddingRight?: number;
}

export type MarkdownTableCellStyle = MarkdownTextStyle &
  Pick<
    MarkdownLayoutStyle,
    | "padding"
    | "paddingHorizontal"
    | "paddingVertical"
    | "paddingLeft"
    | "paddingRight"
    | "paddingTop"
    | "paddingBottom"
  >;

export type MarkdownHeadingLevel = "h1" | "h2" | "h3" | "h4" | "h5" | "h6";

/**
 * Per-element styles for the markdown viewer.
 */
export interface MarkdownStyles {
  blockQuote?: MarkdownTextStyle & MarkdownLayoutStyle;
  bold?: MarkdownTextStyle;
  codeBlock?: MarkdownTextStyle & MarkdownLayoutStyle;
  divider?: MarkdownDividerStyle;
  /** Vertical spacing between blocks. The `style` prop's `gap` wins. */
  gap?: number;
  headings?: Partial<Record<MarkdownHeadingLevel, MarkdownTextStyle>>;
  image?: MarkdownImageStyle;
  inlineCode?: MarkdownInlineCodeStyle;
  italic?: MarkdownTextStyle;
  link?: MarkdownLinkStyle;
  list?: MarkdownListStyle;
  listItem?: MarkdownTextStyle;
  listMarker?: MarkdownListMarkerStyle;
  mention?: MarkdownMentionStyle;
  paragraph?: MarkdownTextStyle;
  spoiler?: MarkdownSpoilerStyle;
  strikethrough?: MarkdownTextStyle;
  subscript?: MarkdownTextStyle;
  superscript?: MarkdownTextStyle;
  table?: MarkdownTableStyle;
  /** Body-row overrides layered on top of `tableRow`. */
  tableBodyRow?: MarkdownLayoutStyle;
  tableCell?: MarkdownTableCellStyle;
  /** Header-cell overrides layered on top of `tableCell`. */
  tableHeaderCell?: MarkdownTableCellStyle;
  /** Header-row overrides layered on top of `tableRow`. */
  tableHeaderRow?: MarkdownLayoutStyle;
  /** Base style for every row; header/body variants layer on top. */
  tableRow?: MarkdownLayoutStyle;
}

/**
 * Pre-sizing data for images referenced in the markdown. Images whose URL is
 * listed here lay out at their final size immediately (zero layout shift);
 * unknown images show a full-width, 200pt-tall placeholder and resize once
 * loaded.
 */
export interface MarkdownImageData {
  height: number;
  url: string;
  width: number;
}

export interface MarkdownUrlEvent {
  url: string;
}

export interface MarkdownImageEvent {
  /** Height of the rendered image, in dp. */
  height: number;
  url: string;
  /** Width of the rendered image, in dp. */
  width: number;
  /** Horizontal position of the rendered image relative to the screen, in dp. */
  x: number;
  /** Vertical position of the rendered image relative to the screen, in dp. */
  y: number;
}

/**
 * The main container style: background, padding, and the gap between
 * blocks, plus base text styles that cascade into every text element
 * (paragraphs, headings, lists, tables, quotes) unless overridden
 * per-element via the `styles` prop. Element builtins survive the cascade:
 * heading sizes/weight and the code block's monospace font stay unless
 * their own section overrides them.
 *
 * For outer layout (margin, width, flex), wrap the component in a View.
 */
export interface MarkdownContainerStyle extends MarkdownTextStyle {
  backgroundColor?: ColorValue;
  /** Vertical spacing between blocks; overrides `styles.gap`. */
  gap?: number;
  /**
   * Editor: caps autogrow — past this height the editor scrolls
   * internally like a textarea. Viewer: plain layout clamp.
   */
  maxHeight?: number;
  padding?: number;
  paddingBottom?: number;
  paddingHorizontal?: number;
  paddingLeft?: number;
  paddingRight?: number;
  paddingTop?: number;
  paddingVertical?: number;
}

/** Formatting active at the cursor / selection. */
export interface MarkdownEditorState {
  /** 0 = no heading. */
  headingLevel: number;
  isBlockQuote: boolean;
  isBold: boolean;
  isCodeBlock: boolean;
  isInlineCode: boolean;
  isItalic: boolean;
  isOrderedList: boolean;
  isSpoiler: boolean;
  isStrikethrough: boolean;
  isSubscript: boolean;
  isSuperscript: boolean;
  isUnorderedList: boolean;
}

export interface MarkdownSelection {
  end: number;
  start: number;
}

export interface MarkdownMentionEvent {
  trigger: string;
}

export interface MarkdownMentionQueryEvent {
  query: string;
  trigger: string;
}

/**
 * Paste payload. `text` is present when the clipboard has text (parsed as
 * markdown and inserted by default); `images` when it has images (reported
 * only, never auto-inserted). `preventDefault()` suppresses the default
 * text insertion.
 */
export interface MarkdownPasteEvent {
  images?: MarkdownImageData[];
  preventDefault(): void;
  text?: string;
}

export interface FastMarkdownEditorRef {
  blur(): void;
  focus(): void;
  /** Resolves the markdown as of the latest edit. */
  getMarkdown(): Promise<string>;
  /**
   * Links the selection to `url`; with a collapsed cursor, inserts `label`
   * (or the URL itself) as linked text.
   */
  insertLink(url: string, label?: string): void;
  /** Parses `markdown` and inserts it at the cursor (or over the selection). */
  insertMarkdown(markdown: string): void;
  /**
   * Inserts an atomic mention token (`trigger + label`, e.g. `@ali`)
   * linked to `url` (an app scheme like `users://ali`), replacing any
   * active mention query.
   */
  insertMention(trigger: string, label: string, url: string): void;
  /** Removes the link covering the selection or cursor. */
  removeLink(): void;
  setSelection(start: number, end: number): void;
  /** Replaces the content; the value is parsed as markdown. */
  setValue(markdown: string): void;
  /** Block toggles apply to every line the selection touches. */
  toggleBlockQuote(): void;
  /**
   * Each inline toggle applies to the selection, or arms/disarms the mark
   * for text typed at the collapsed cursor.
   */
  toggleBold(): void;
  toggleCode(): void;
  toggleCodeBlock(): void;
  /** Toggles the heading level (1-6) on the selected lines. */
  toggleHeading(level: number): void;
  toggleItalic(): void;
  toggleOrderedList(): void;
  toggleSpoiler(): void;
  toggleStrikethrough(): void;
  toggleSubscript(): void;
  toggleSuperscript(): void;
  toggleUnorderedList(): void;
}

export interface FastMarkdownEditorProps {
  /** Scale text with the system font size setting. Defaults to true. */
  allowFontScaling?: boolean;
  autoCapitalize?: "none" | "sentences" | "words" | "characters";
  autoCorrect?: boolean;
  autoFocus?: boolean;
  /**
   * Caret color. On iOS the caret and selection share one tint, so
   * `selectionColor` wins when both are set. Android 10+ only. Platform
   * colors (`PlatformColor`, `DynamicColorIOS`) are supported.
   */
  cursorColor?: ColorValue;
  /** Initial markdown content, applied once. */
  defaultValue?: string;
  editable?: boolean;
  /** Trigger characters (e.g. '@', '#') that begin a mention. */
  mentionTriggers?: string[];
  multiline?: boolean;
  onBlur?: () => void;
  onChangeMarkdown?: (markdown: string) => void;
  onChangeSelection?: (selection: MarkdownSelection) => void;
  onChangeState?: (state: MarkdownEditorState) => void;
  onChangeText?: (text: string) => void;
  onFocus?: () => void;
  onLinkDetected?: (event: MarkdownUrlEvent) => void;
  onMentionChange?: (event: MarkdownMentionQueryEvent) => void;
  onMentionEnd?: (event: MarkdownMentionEvent) => void;
  onMentionStart?: (event: MarkdownMentionEvent) => void;
  onPaste?: (event: MarkdownPasteEvent) => void;
  placeholder?: string;
  placeholderTextColor?: ColorValue;
  scrollEnabled?: boolean;
  /**
   * Selection highlight color. On iOS this also tints the caret (UIKit
   * shares one tint). Platform colors (`PlatformColor`, `DynamicColorIOS`)
   * are supported.
   */
  selectionColor?: ColorValue;
  /** Same container style contract as the viewer. */
  style?: StyleProp<MarkdownContainerStyle>;
  /** Per-element markdown styles; shared shape with the viewer. */
  styles?: MarkdownStyles;
}

export interface FastMarkdownViewProps {
  /** Scale text with the system font size setting. Defaults to true. */
  allowFontScaling?: boolean;
  images?: MarkdownImageData[];
  /** The markdown source to render. */
  markdown: string;
  onImagePress?: (event: MarkdownImageEvent) => void;
  onLinkLongPress?: (event: MarkdownUrlEvent) => void;
  onLinkPress?: (event: MarkdownUrlEvent) => void;
  /**
   * Main container style: `backgroundColor`, `padding*`, `gap`, and base
   * text styles that cascade into every text element unless overridden via
   * `styles`. All keys are measured natively.
   */
  style?: StyleProp<MarkdownContainerStyle>;
  /** Per-element markdown styles. Hoist to module scope or memoize. */
  styles?: MarkdownStyles;
}
