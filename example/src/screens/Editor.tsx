import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  PlatformColor,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
} from "react-native";
import {
  JetMarkdownEditor,
  type MarkdownContainerStyle,
  type MarkdownEditorState,
  mergeStyles,
  useJetMarkdownEditor,
} from "react-native-jet-markdown";

const styles = mergeStyles();

const editorStyle: MarkdownContainerStyle = {
  backgroundColor: "#F9FAFB",
  fontSize: 16,
  lineHeight: 24,
  maxHeight: 160,
  padding: 12,
};

export function Editor() {
  const editor = useJetMarkdownEditor();
  const [status, setStatus] = useState("idle");
  const [selection, setSelection] = useState("0:0");
  const [lastMarkdown, setLastMarkdown] = useState("");
  const [state, setState] = useState<MarkdownEditorState | null>(null);
  const [mention, setMention] = useState("");

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      style={sheet.container}
    >
      <ScrollView keyboardDismissMode="interactive" style={sheet.scroll}>
        <Text style={sheet.status}>
          {status} · sel {selection}
          {mention ? ` · ${mention}` : ""}
        </Text>
        <JetMarkdownEditor
          mentionTriggers={["@", "#"]}
          onBlur={() => setStatus("blurred")}
          onChangeMarkdown={(markdown) => setLastMarkdown(markdown)}
          onChangeSelection={(range) =>
            setSelection(`${range.start}:${range.end}`)
          }
          onChangeState={setState}
          onFocus={() => setStatus("focused")}
          onLinkDetected={(event) => setMention(`link? ${event.url}`)}
          onMentionChange={(event) =>
            setMention(`mention ${event.trigger}${event.query}`)
          }
          onMentionEnd={() => setMention("")}
          onMentionStart={(event) => setMention(`mention ${event.trigger}`)}
          placeholder="Write something..."
          placeholderTextColor="#9CA3AF"
          ref={editor.ref}
          selectionColor={Platform.select({
            android: PlatformColor("?attr/colorPrimary"),
            ios: PlatformColor("systemTealColor"),
          })}
          style={editorStyle}
          styles={styles}
        />
        <Text style={sheet.output} testID="markdown-output">
          {lastMarkdown || "(empty)"}
        </Text>
      </ScrollView>
      <ScrollView
        contentContainerStyle={sheet.toolbarContent}
        horizontal
        keyboardShouldPersistTaps="always"
        style={sheet.toolbar}
      >
        <ToolbarButton
          active={state?.isBold}
          label="B"
          onPress={editor.toggleBold}
        />
        <ToolbarButton
          active={state?.isItalic}
          label="I"
          onPress={editor.toggleItalic}
        />
        <ToolbarButton
          active={state?.isStrikethrough}
          label="S"
          onPress={editor.toggleStrikethrough}
        />
        <ToolbarButton
          active={state?.isInlineCode}
          label="Code"
          onPress={editor.toggleCode}
        />
        <ToolbarButton
          active={state?.isSpoiler}
          label="Spoiler"
          onPress={editor.toggleSpoiler}
        />
        <ToolbarButton
          active={state?.isSuperscript}
          label="Sup"
          onPress={editor.toggleSuperscript}
        />
        <ToolbarButton
          active={state?.isSubscript}
          label="Sub"
          onPress={editor.toggleSubscript}
        />
      </ScrollView>
      <ScrollView
        contentContainerStyle={sheet.toolbarContent}
        horizontal
        keyboardShouldPersistTaps="always"
        style={sheet.toolbar}
      >
        <ToolbarButton
          active={state?.headingLevel === 1}
          label="H1"
          onPress={() => editor.toggleHeading(1)}
        />
        <ToolbarButton
          active={state?.headingLevel === 2}
          label="H2"
          onPress={() => editor.toggleHeading(2)}
        />
        <ToolbarButton
          active={state?.isBlockQuote}
          label="Quote"
          onPress={editor.toggleBlockQuote}
        />
        <ToolbarButton
          active={state?.isCodeBlock}
          label="Code blk"
          onPress={editor.toggleCodeBlock}
        />
        <ToolbarButton
          active={state?.isUnorderedList}
          label="• List"
          onPress={editor.toggleUnorderedList}
        />
        <ToolbarButton
          active={state?.isOrderedList}
          label="1. List"
          onPress={editor.toggleOrderedList}
        />
        <ToolbarButton
          label="Link"
          onPress={() => editor.insertLink("https://example.com", "example")}
        />
        <ToolbarButton label="Unlink" onPress={editor.removeLink} />
        <ToolbarButton
          label="@ali"
          onPress={() => editor.insertMention("@", "ali", "users://ali")}
        />
        <ToolbarButton
          label="#general"
          onPress={() =>
            editor.insertMention("#", "general", "channels://general")
          }
        />
      </ScrollView>
      <ScrollView
        contentContainerStyle={sheet.toolbarContent}
        horizontal
        keyboardShouldPersistTaps="always"
        style={sheet.toolbar}
      >
        <ToolbarButton label="Focus" onPress={editor.focus} />
        <ToolbarButton label="Blur" onPress={editor.blur} />
        <ToolbarButton
          label="Set value"
          onPress={() => editor.setValue("Hello **world** from setValue")}
        />
        <ToolbarButton
          label="Select 0-5"
          onPress={() => editor.setSelection(0, 5)}
        />
        <ToolbarButton
          label="Get markdown"
          onPress={async () => {
            const markdown = await editor.getMarkdown();
            setLastMarkdown(markdown);
          }}
        />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function ToolbarButton({
  active,
  label,
  onPress,
}: {
  active?: boolean;
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={[sheet.button, active === true && sheet.buttonActive]}
    >
      <Text
        style={[sheet.buttonText, active === true && sheet.buttonTextActive]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const sheet = StyleSheet.create({
  container: {
    flex: 1,
  },
  scroll: {
    flex: 1,
    padding: 12,
  },
  status: {
    color: "#6B7280",
    fontSize: 12,
    marginBottom: 8,
  },
  output: {
    color: "#374151",
    fontFamily: Platform.OS === "ios" ? "Menlo" : "monospace",
    fontSize: 12,
    marginTop: 12,
  },
  toolbar: {
    borderTopColor: "#E5E7EB",
    borderTopWidth: 1,
    flexGrow: 0,
  },
  toolbarContent: {
    gap: 6,
    padding: 8,
  },
  button: {
    backgroundColor: "#F3F4F6",
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  buttonActive: {
    backgroundColor: "#111827",
  },
  buttonText: {
    color: "#111827",
    fontSize: 13,
  },
  buttonTextActive: {
    color: "#F9FAFB",
  },
});
