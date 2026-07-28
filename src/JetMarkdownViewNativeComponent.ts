import {
  type CodegenTypes,
  codegenNativeComponent,
  type ViewProps,
} from "react-native";

interface UrlEvent {
  url: string;
}

interface ImagePressEvent {
  height: CodegenTypes.Double;
  url: string;
  width: CodegenTypes.Double;
  x: CodegenTypes.Double;
  y: CodegenTypes.Double;
}

interface ImageData {
  height: CodegenTypes.Double;
  url: string;
  width: CodegenTypes.Double;
}

interface NativeProps extends ViewProps {
  allowFontScaling?: CodegenTypes.WithDefault<boolean, true>;
  images?: readonly ImageData[];
  markdown: string;
  onImagePress?: CodegenTypes.DirectEventHandler<ImagePressEvent>;
  onLinkLongPress?: CodegenTypes.DirectEventHandler<UrlEvent>;
  onLinkPress?: CodegenTypes.DirectEventHandler<UrlEvent>;
  stylesJson?: string;
}

export default codegenNativeComponent<NativeProps>("JetMarkdownView");
