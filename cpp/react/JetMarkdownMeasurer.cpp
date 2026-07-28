#include "JetMarkdownMeasurer.h"

namespace jetmarkdown {

JetMarkdownMeasurer& JetMarkdownMeasurer::shared() {
  static JetMarkdownMeasurer instance;
  return instance;
}

void JetMarkdownMeasurer::install(MeasureFunction fn) {
  std::lock_guard<std::mutex> lock(mutex_);
  fn_ = std::move(fn);
}

float JetMarkdownMeasurer::measure(
    const std::string& markdown,
    const std::string& stylesJson,
    const std::string& imagesJson,
    float maxWidth,
    float fontScale) const {
  MeasureFunction fn;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    fn = fn_;
  }
  if (!fn) {
    return 0.0f;
  }
  return fn(markdown, stylesJson, imagesJson, maxWidth, fontScale);
}

} // namespace jetmarkdown
