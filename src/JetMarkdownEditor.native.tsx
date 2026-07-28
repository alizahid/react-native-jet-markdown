import {
  type Ref,
  useCallback,
  useImperativeHandle,
  useMemo,
  useRef,
} from "react";
import type { NativeSyntheticEvent } from "react-native";

import NativeJetMarkdownEditor, {
  Commands,
} from "./JetMarkdownEditorNativeComponent";
import {
  type MainStyle,
  serializeStyles,
  splitContainerStyle,
} from "./serializeStyles";
import type {
  JetMarkdownEditorProps,
  JetMarkdownEditorRef,
  MarkdownImageData,
  MarkdownSelection,
} from "./types";

type NativeEditor = React.ElementRef<typeof NativeJetMarkdownEditor>;

export function JetMarkdownEditor({
  allowFontScaling,
  autoCapitalize,
  autoCorrect,
  autoFocus,
  cursorColor,
  defaultValue,
  editable,
  mentionTriggers,
  multiline,
  onBlur,
  onChangeMarkdown,
  onChangeSelection,
  onChangeState,
  onChangeText,
  onFocus,
  onLinkDetected,
  onMentionChange,
  onMentionEnd,
  onMentionStart,
  onPaste,
  placeholder,
  placeholderTextColor,
  scrollEnabled,
  selectionColor,
  style,
  styles,
  ref,
}: JetMarkdownEditorProps & { ref?: Ref<JetMarkdownEditorRef> }) {
  const nativeRef = useRef<NativeEditor>(null);
  // getMarkdown resolves from the latest onEditorChangeMarkdown payload;
  // until the serializer emits (E1), plain text doubles as the markdown.
  const markdownRef = useRef<string | null>(null);
  const textRef = useRef<string>(defaultValue ?? "");

  const { hostStyle, main } = splitContainerStyle(style);
  // maxHeight rides as a native prop (autogrow cap; the editor scrolls
  // internally past it) instead of a host style key. It is omitted when
  // unset: Yoga reads the same raw prop as max-height, and a 0 would clamp
  // flex-sized editors to zero height.
  const { maxHeight, ...editorHostStyle } = hostStyle;
  const mainKey = JSON.stringify(main);
  const stylesJson = useMemo(
    () => serializeStyles(styles, JSON.parse(mainKey) as MainStyle),
    [styles, mainKey]
  );

  useImperativeHandle(
    ref,
    (): JetMarkdownEditorRef => ({
      blur: () => {
        if (nativeRef.current) {
          Commands.blur(nativeRef.current);
        }
      },
      focus: () => {
        if (nativeRef.current) {
          Commands.focus(nativeRef.current);
        }
      },
      getMarkdown: () =>
        Promise.resolve(markdownRef.current ?? textRef.current),
      insertLink: (url: string, label?: string) => {
        if (nativeRef.current) {
          Commands.insertLink(nativeRef.current, url, label ?? "");
        }
      },
      insertMarkdown: (markdown: string) => {
        if (nativeRef.current) {
          Commands.insertMarkdown(nativeRef.current, markdown);
        }
      },
      insertMention: (trigger: string, label: string, url: string) => {
        if (nativeRef.current) {
          Commands.insertMention(nativeRef.current, trigger, label, url);
        }
      },
      removeLink: () => {
        if (nativeRef.current) {
          Commands.removeLink(nativeRef.current);
        }
      },
      setSelection: (start: number, end: number) => {
        if (nativeRef.current) {
          Commands.setSelection(nativeRef.current, start, end);
        }
      },
      setValue: (markdown: string) => {
        // Seed with the value being set so getMarkdown() never reports the
        // pre-setValue document; native re-emits its canonical serialization
        // right after.
        markdownRef.current = markdown;
        textRef.current = markdown;
        if (nativeRef.current) {
          Commands.setValue(nativeRef.current, markdown);
        }
      },
      toggleBlockQuote: () => {
        if (nativeRef.current) {
          Commands.toggleBlockQuote(nativeRef.current);
        }
      },
      toggleBold: () => {
        if (nativeRef.current) {
          Commands.toggleBold(nativeRef.current);
        }
      },
      toggleCode: () => {
        if (nativeRef.current) {
          Commands.toggleCode(nativeRef.current);
        }
      },
      toggleCodeBlock: () => {
        if (nativeRef.current) {
          Commands.toggleCodeBlock(nativeRef.current);
        }
      },
      toggleHeading: (level: number) => {
        if (nativeRef.current) {
          Commands.toggleHeading(nativeRef.current, level);
        }
      },
      toggleItalic: () => {
        if (nativeRef.current) {
          Commands.toggleItalic(nativeRef.current);
        }
      },
      toggleOrderedList: () => {
        if (nativeRef.current) {
          Commands.toggleOrderedList(nativeRef.current);
        }
      },
      toggleSpoiler: () => {
        if (nativeRef.current) {
          Commands.toggleSpoiler(nativeRef.current);
        }
      },
      toggleStrikethrough: () => {
        if (nativeRef.current) {
          Commands.toggleStrikethrough(nativeRef.current);
        }
      },
      toggleSubscript: () => {
        if (nativeRef.current) {
          Commands.toggleSubscript(nativeRef.current);
        }
      },
      toggleSuperscript: () => {
        if (nativeRef.current) {
          Commands.toggleSuperscript(nativeRef.current);
        }
      },
      toggleUnorderedList: () => {
        if (nativeRef.current) {
          Commands.toggleUnorderedList(nativeRef.current);
        }
      },
    }),
    []
  );

  // The native side never inserts pasted content itself: it reports the
  // clipboard here, and unless the app's onPaste calls preventDefault()
  // synchronously, the default insertion happens via insertMarkdown.
  const handlePaste = useCallback(
    (
      event: NativeSyntheticEvent<{
        images: readonly MarkdownImageData[];
        text: string;
      }>
    ) => {
      const { images, text } = event.nativeEvent;
      let prevented = false;
      onPaste?.({
        images: images.length > 0 ? [...images] : undefined,
        preventDefault: () => {
          prevented = true;
        },
        text: text.length > 0 ? text : undefined,
      });
      if (!prevented && text.length > 0 && nativeRef.current) {
        Commands.insertMarkdown(nativeRef.current, text);
      }
    },
    [onPaste]
  );

  return (
    <NativeJetMarkdownEditor
      allowFontScaling={allowFontScaling}
      autoCapitalize={autoCapitalize}
      autoCorrect={autoCorrect}
      autoFocus={autoFocus}
      cursorColor={cursorColor}
      defaultValue={defaultValue}
      editable={editable}
      maxHeight={
        typeof maxHeight === "number" && maxHeight > 0 ? maxHeight : undefined
      }
      mentionTriggers={mentionTriggers}
      multiline={multiline}
      onEditorBlur={onBlur ? () => onBlur() : undefined}
      onEditorChangeMarkdown={(
        event: NativeSyntheticEvent<{ markdown: string }>
      ) => {
        markdownRef.current = event.nativeEvent.markdown;
        onChangeMarkdown?.(event.nativeEvent.markdown);
      }}
      onEditorChangeSelection={
        onChangeSelection
          ? (event: NativeSyntheticEvent<MarkdownSelection>) =>
              onChangeSelection({
                end: event.nativeEvent.end,
                start: event.nativeEvent.start,
              })
          : undefined
      }
      onEditorChangeState={
        onChangeState
          ? (event) => {
              // Copy the declared fields so consumers never see the raw
              // nativeEvent extras (target, etc.).
              const state = event.nativeEvent;
              onChangeState({
                headingLevel: state.headingLevel,
                isBlockQuote: state.isBlockQuote,
                isBold: state.isBold,
                isCodeBlock: state.isCodeBlock,
                isInlineCode: state.isInlineCode,
                isItalic: state.isItalic,
                isOrderedList: state.isOrderedList,
                isSpoiler: state.isSpoiler,
                isStrikethrough: state.isStrikethrough,
                isSubscript: state.isSubscript,
                isSuperscript: state.isSuperscript,
                isUnorderedList: state.isUnorderedList,
              });
            }
          : undefined
      }
      onEditorChangeText={(event: NativeSyntheticEvent<{ text: string }>) => {
        textRef.current = event.nativeEvent.text;
        onChangeText?.(event.nativeEvent.text);
      }}
      onEditorFocus={onFocus ? () => onFocus() : undefined}
      onEditorLinkDetected={
        onLinkDetected
          ? (event: NativeSyntheticEvent<{ url: string }>) =>
              onLinkDetected({ url: event.nativeEvent.url })
          : undefined
      }
      onEditorMentionChange={
        onMentionChange
          ? (event) => onMentionChange(event.nativeEvent)
          : undefined
      }
      onEditorMentionEnd={
        onMentionEnd ? (event) => onMentionEnd(event.nativeEvent) : undefined
      }
      onEditorMentionStart={
        onMentionStart
          ? (event) => onMentionStart(event.nativeEvent)
          : undefined
      }
      onEditorPaste={handlePaste}
      placeholder={placeholder}
      placeholderTextColor={placeholderTextColor}
      ref={nativeRef}
      scrollEnabled={scrollEnabled}
      selectionColor={selectionColor}
      style={editorHostStyle}
      stylesJson={stylesJson}
    />
  );
}
