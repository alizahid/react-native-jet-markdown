import { useMemo } from "react";
import type { NativeSyntheticEvent } from "react-native";

import NativeJetMarkdownView from "./JetMarkdownViewNativeComponent";
import {
  type MainStyle,
  serializeStyles,
  splitContainerStyle,
} from "./serializeStyles";
import type {
  JetMarkdownViewProps,
  MarkdownImageEvent,
  MarkdownUrlEvent,
} from "./types";

export function JetMarkdownView({
  allowFontScaling,
  markdown,
  style,
  styles,
  images,
  onLinkPress,
  onLinkLongPress,
  onImagePress,
}: JetMarkdownViewProps) {
  const { hostStyle, main } = splitContainerStyle(style);

  const mainKey = JSON.stringify(main);
  const stylesJson = useMemo(
    () => serializeStyles(styles, JSON.parse(mainKey) as MainStyle),
    [styles, mainKey]
  );

  return (
    <NativeJetMarkdownView
      allowFontScaling={allowFontScaling}
      images={images}
      markdown={markdown}
      onImagePress={
        onImagePress
          ? (event: NativeSyntheticEvent<MarkdownImageEvent>) =>
              onImagePress({
                height: event.nativeEvent.height,
                url: event.nativeEvent.url,
                width: event.nativeEvent.width,
                x: event.nativeEvent.x,
                y: event.nativeEvent.y,
              })
          : undefined
      }
      onLinkLongPress={
        onLinkLongPress
          ? (event: NativeSyntheticEvent<MarkdownUrlEvent>) =>
              onLinkLongPress({ url: event.nativeEvent.url })
          : undefined
      }
      onLinkPress={
        onLinkPress
          ? (event: NativeSyntheticEvent<MarkdownUrlEvent>) =>
              onLinkPress({ url: event.nativeEvent.url })
          : undefined
      }
      style={hostStyle}
      stylesJson={stylesJson}
    />
  );
}
