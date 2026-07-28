# Regression canary: mimics a sibling pod that vendors generically named
# headers (md4c.h, Parser.h) and includes them via HEADER_SEARCH_PATHS.
# Xcode's project-wide header maps resolve quoted includes BY BASENAME
# across every pod in the project, so if JetMarkdown ever puts its own
# md4c.h / Parser.h back into the Pods project, this pod picks up the
# wrong headers and stops compiling — exactly what happened to
# react-native-enriched-markdown and react-native-unistyles.
Pod::Spec.new do |s|
  s.name         = "HeaderCollisionCanary"
  s.version      = "1.0.0"
  s.summary      = "Build-time canary for header-map basename collisions"
  s.homepage     = "https://github.com/alizahid/react-native-jet-markdown"
  s.license      = "MIT"
  s.authors      = "Ali Zahid"
  s.source       = { :path => "." }
  s.platforms    = { :ios => "15.1" }

  s.source_files = "Canary.mm", "vendor/*.h"
  s.pod_target_xcconfig = {
    "HEADER_SEARCH_PATHS" => '"$(PODS_TARGET_SRCROOT)/vendor"',
  }
end
