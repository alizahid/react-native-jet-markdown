#pragma once

#ifdef RN_SERIALIZABLE_STATE
#include <folly/dynamic.h>
#endif

namespace facebook::react {

// Shadow-node state: the editor's current content height, published by the
// platform view whenever the text changes so Fabric re-measures (autogrow).
class JetMarkdownEditorState final {
 public:
  JetMarkdownEditorState() = default;
  explicit JetMarkdownEditorState(double height) : height(height) {}

#ifdef RN_SERIALIZABLE_STATE
  JetMarkdownEditorState(
      const JetMarkdownEditorState& previousState,
      folly::dynamic data) {
    height = previousState.height;
    const auto& value = data["height"];
    if (value.isNumber()) {
      height = value.getDouble();
    }
  }

  folly::dynamic getDynamic() const {
    return folly::dynamic::object("height", height);
  }
#endif

  double height = 0;
};

} // namespace facebook::react
