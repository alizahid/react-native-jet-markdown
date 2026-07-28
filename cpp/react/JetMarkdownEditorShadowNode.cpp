#include "JetMarkdownEditorShadowNode.h"

#include <algorithm>
#include <cmath>

#include "JetMarkdownMeasurer.h"

namespace facebook::react {

Size JetMarkdownEditorShadowNode::measureContent(
    const LayoutContext& layoutContext,
    const LayoutConstraints& layoutConstraints) const {
  const auto& props = getConcreteProps();
  const auto& state = getStateData();

  Float maxWidth = layoutConstraints.maximumSize.width;
  if (!std::isfinite(maxWidth)) {
    maxWidth = std::max(layoutConstraints.minimumSize.width, Float{0});
  }

  Float height = static_cast<Float>(state.height);
  if (height <= 0) {
    // Initial layout: size the defaultValue (or one empty line) with the
    // shared measurer so the editor mounts at its real height.
    const std::string& markdown =
        props.defaultValue.empty() ? " " : props.defaultValue;
    float fontScale = 1.0f;
    if (props.allowFontScaling && layoutContext.fontSizeMultiplier > 0) {
      fontScale = static_cast<float>(layoutContext.fontSizeMultiplier);
    }
    height = static_cast<Float>(jetmarkdown::JetMarkdownMeasurer::shared().measure(
        markdown,
        props.stylesJson,
        "{}",
        static_cast<float>(maxWidth),
        fontScale));
  }

  // Autogrow cap: past maxHeight the view scrolls internally instead of
  // growing (the view publishes capped heights too; this covers the
  // initial defaultValue measurement).
  if (props.maxHeight > 0) {
    height = std::min(height, static_cast<Float>(props.maxHeight));
  }

  height = std::clamp(
      height,
      layoutConstraints.minimumSize.height,
      layoutConstraints.maximumSize.height);

  return Size{maxWidth, height};
}

} // namespace facebook::react
