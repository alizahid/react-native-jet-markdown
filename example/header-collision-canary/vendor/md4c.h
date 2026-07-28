// Stand-in for a FORKED md4c (react-native-enriched-markdown ships one
// with spoiler extensions). The canary asserts that a quoted include of
// "md4c.h" resolves to THIS file, not JetMarkdown's vanilla copy.
#pragma once

#define CANARY_MD4C_FORK 1

typedef enum {
  MD_SPAN_SPOILER_CANARY = 99,
} CanarySpanType;
