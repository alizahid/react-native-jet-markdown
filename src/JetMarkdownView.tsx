import { type StyleProp, Text, View, type ViewStyle } from "react-native";

import type { JetMarkdownViewProps } from "./types";

// Non-native (web) fallback: renders the raw markdown as plain text.
export function JetMarkdownView({
  allowFontScaling,
  markdown,
  style,
}: JetMarkdownViewProps) {
  return (
    <View style={style as StyleProp<ViewStyle>}>
      <Text allowFontScaling={allowFontScaling}>{markdown}</Text>
    </View>
  );
}
