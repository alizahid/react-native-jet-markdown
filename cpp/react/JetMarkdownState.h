#pragma once

#include <map>
#include <string>

#ifdef RN_SERIALIZABLE_STATE
#include <folly/dynamic.h>
#endif

namespace facebook::react {

// Shadow-node state: intrinsic sizes of images discovered after loading.
// The host view publishes sizes here; measureContent reads them so the view
// grows once an un-presized image arrives.
class JetMarkdownState final {
 public:
  struct ImageSize {
    double width = 0;
    double height = 0;
  };

  JetMarkdownState() = default;
  explicit JetMarkdownState(std::map<std::string, ImageSize> imageSizes)
      : imageSizes(std::move(imageSizes)) {}

#ifdef RN_SERIALIZABLE_STATE
  JetMarkdownState(const JetMarkdownState& previousState, folly::dynamic data) {
    imageSizes = previousState.imageSizes;
    const auto& sizes = data["imageSizes"];
    if (sizes.isObject()) {
      for (const auto& entry : sizes.items()) {
        imageSizes[entry.first.getString()] = ImageSize{
            entry.second["width"].getDouble(),
            entry.second["height"].getDouble(),
        };
      }
    }
  }

  folly::dynamic getDynamic() const {
    folly::dynamic sizes = folly::dynamic::object();
    for (const auto& [url, size] : imageSizes) {
      sizes[url] = folly::dynamic::object("width", size.width)("height", size.height);
    }
    return folly::dynamic::object("imageSizes", std::move(sizes));
  }
#endif

  std::map<std::string, ImageSize> imageSizes;
};

} // namespace facebook::react
