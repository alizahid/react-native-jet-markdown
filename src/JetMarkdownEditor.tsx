import { type Ref, useImperativeHandle, useRef } from "react";
import { type StyleProp, TextInput, type TextStyle } from "react-native";

import type { JetMarkdownEditorProps, JetMarkdownEditorRef } from "./types";

// Non-native (web) fallback: a plain multiline text input over raw markdown.
export function JetMarkdownEditor({
  allowFontScaling,
  autoCapitalize,
  autoCorrect,
  autoFocus,
  cursorColor,
  defaultValue,
  editable,
  multiline = true,
  onBlur,
  onChangeMarkdown,
  onChangeText,
  onFocus,
  placeholder,
  placeholderTextColor,
  scrollEnabled,
  selectionColor,
  style,
  ref,
}: JetMarkdownEditorProps & { ref?: Ref<JetMarkdownEditorRef> }) {
  const inputRef = useRef<React.ComponentRef<typeof TextInput>>(null);
  const textRef = useRef(defaultValue ?? "");

  useImperativeHandle(
    ref,
    (): JetMarkdownEditorRef => ({
      blur: () => inputRef.current?.blur(),
      focus: () => inputRef.current?.focus(),
      getMarkdown: () => Promise.resolve(textRef.current),
      insertLink: () => undefined,
      insertMarkdown: () => undefined,
      insertMention: () => undefined,
      removeLink: () => undefined,
      setSelection: () => {
        // Not supported in the web fallback.
      },
      setValue: (markdown: string) => {
        textRef.current = markdown;
        inputRef.current?.setNativeProps({ text: markdown });
      },
      // Formatting toggles are not supported in the web fallback (raw
      // markdown).
      toggleBlockQuote: () => undefined,
      toggleBold: () => undefined,
      toggleCode: () => undefined,
      toggleCodeBlock: () => undefined,
      toggleHeading: () => undefined,
      toggleItalic: () => undefined,
      toggleOrderedList: () => undefined,
      toggleSpoiler: () => undefined,
      toggleStrikethrough: () => undefined,
      toggleSubscript: () => undefined,
      toggleSuperscript: () => undefined,
      toggleUnorderedList: () => undefined,
    }),
    []
  );

  return (
    <TextInput
      allowFontScaling={allowFontScaling}
      autoCapitalize={autoCapitalize}
      autoCorrect={autoCorrect}
      autoFocus={autoFocus}
      cursorColor={cursorColor}
      defaultValue={defaultValue}
      editable={editable}
      multiline={multiline}
      onBlur={onBlur}
      onChangeText={(text) => {
        textRef.current = text;
        onChangeText?.(text);
        onChangeMarkdown?.(text);
      }}
      onFocus={onFocus}
      placeholder={placeholder}
      placeholderTextColor={placeholderTextColor}
      ref={inputRef}
      scrollEnabled={scrollEnabled}
      selectionColor={selectionColor}
      style={style as StyleProp<TextStyle>}
    />
  );
}
