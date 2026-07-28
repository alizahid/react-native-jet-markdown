#pragma once

#include <functional>
#include <mutex>
#include <string>

namespace jetmarkdown {

// Seam between the platform-agnostic shadow node and platform text layout.
// iOS installs a lambda over JMDMarkdownMeasurer; Android installs a JNI
// call into MarkdownMeasurer.kt. Runs on the Fabric layout thread.
class JetMarkdownMeasurer {
 public:
  // imagesJson: {"<url>":[width,height],...} merged from the images prop
  // and the shadow-node state (sizes discovered after load).
  using MeasureFunction = std::function<float(
      const std::string& markdown,
      const std::string& stylesJson,
      const std::string& imagesJson,
      float maxWidth,
      float fontScale)>;

  static JetMarkdownMeasurer& shared();

  void install(MeasureFunction fn);

  // Returns the content height for the given constraints; 0 when no
  // platform measurer is installed yet.
  float measure(
      const std::string& markdown,
      const std::string& stylesJson,
      const std::string& imagesJson,
      float maxWidth,
      float fontScale) const;

 private:
  mutable std::mutex mutex_;
  MeasureFunction fn_;
};

} // namespace jetmarkdown
