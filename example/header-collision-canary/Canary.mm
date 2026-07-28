// Quoted includes resolved through HEADER_SEARCH_PATHS — interceptable by
// the project-wide header maps, which is the point of this canary.
#include "Parser.h"
#include "md4c.h"

#ifndef CANARY_MD4C_FORK
#error "md4c.h resolved to a foreign copy - JetMarkdown headers leaked into the Pods project"
#endif

static_assert(
    MD_SPAN_SPOILER_CANARY == 99, "forked md4c.h symbols must be visible");

namespace {
canary::parser::Parser makeParser() {
  return canary::parser::Parser{.value = 1};
}
} // namespace

__attribute__((unused)) static const int kCanary = makeParser().value;
