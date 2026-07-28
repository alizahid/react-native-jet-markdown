import { useMemo, useRef } from "react";

import type { JetMarkdownEditorRef } from "./types";

/**
 * Convenience hook: a ref to pass to `<JetMarkdownEditor>` plus stable
 * callbacks for every editor command.
 *
 * ```tsx
 * const editor = useJetMarkdownEditor();
 * <JetMarkdownEditor ref={editor.ref} />
 * <Button onPress={editor.focus} />
 * ```
 */
export function useJetMarkdownEditor() {
  const ref = useRef<JetMarkdownEditorRef>(null);

  return useMemo(
    () => ({
      blur: () => ref.current?.blur(),
      focus: () => ref.current?.focus(),
      getMarkdown: (): Promise<string> =>
        ref.current?.getMarkdown() ?? Promise.resolve(""),
      insertLink: (url: string, label?: string) =>
        ref.current?.insertLink(url, label),
      insertMarkdown: (markdown: string) =>
        ref.current?.insertMarkdown(markdown),
      insertMention: (trigger: string, label: string, url: string) =>
        ref.current?.insertMention(trigger, label, url),
      ref,
      removeLink: () => ref.current?.removeLink(),
      setSelection: (start: number, end: number) =>
        ref.current?.setSelection(start, end),
      setValue: (markdown: string) => ref.current?.setValue(markdown),
      toggleBlockQuote: () => ref.current?.toggleBlockQuote(),
      toggleBold: () => ref.current?.toggleBold(),
      toggleCode: () => ref.current?.toggleCode(),
      toggleCodeBlock: () => ref.current?.toggleCodeBlock(),
      toggleHeading: (level: number) => ref.current?.toggleHeading(level),
      toggleItalic: () => ref.current?.toggleItalic(),
      toggleOrderedList: () => ref.current?.toggleOrderedList(),
      toggleSpoiler: () => ref.current?.toggleSpoiler(),
      toggleStrikethrough: () => ref.current?.toggleStrikethrough(),
      toggleSubscript: () => ref.current?.toggleSubscript(),
      toggleSuperscript: () => ref.current?.toggleSuperscript(),
      toggleUnorderedList: () => ref.current?.toggleUnorderedList(),
    }),
    []
  );
}
