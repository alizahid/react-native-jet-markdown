#include "Preprocess.h"

namespace jetmarkdown {

namespace {

bool isFenceLine(const std::string& s, size_t lineStart, size_t lineEnd) {
  size_t i = lineStart;
  size_t spaces = 0;
  while (i < lineEnd && s[i] == ' ' && spaces < 3) {
    i++;
    spaces++;
  }
  if (i >= lineEnd) {
    return false;
  }
  char fence = s[i];
  if (fence != '`' && fence != '~') {
    return false;
  }
  size_t run = 0;
  while (i < lineEnd && s[i] == fence) {
    i++;
    run++;
  }
  return run >= 3;
}

// md4c strips backslashes before the inline-extension scanner runs, so an
// escaped extension delimiter (\| \^ \~) would be re-assembled and re-trigger
// spoilers/sup/sub. Rewrite them to entity forms here, where the backslash is
// still visible; the parser materializes entities as verbatim text the
// scanner skips. Backtick spans are honored (their backslashes are literal
// code content), approximated per line.
void appendWithEscapeRewrite(
    const std::string& in, size_t start, size_t end, std::string& out) {
  size_t i = start;
  while (i < end) {
    const char c = in[i];
    if (c == '`') {
      // Enter a code span: find the closing run of the same length and copy
      // the whole span untouched. No closer -> literal backticks.
      size_t runLen = 0;
      size_t j = i;
      while (j < end && in[j] == '`') {
        j++;
        runLen++;
      }
      size_t close = std::string::npos;
      size_t scan = j;
      while (scan < end) {
        if (in[scan] != '`') {
          scan++;
          continue;
        }
        size_t closeLen = 0;
        size_t r = scan;
        while (r < end && in[r] == '`') {
          r++;
          closeLen++;
        }
        if (closeLen == runLen) {
          close = scan;
          break;
        }
        scan = r;
      }
      if (close != std::string::npos) {
        out.append(in, i, (close + runLen) - i);
        i = close + runLen;
      } else {
        out.append(in, i, j - i);
        i = j;
      }
      continue;
    }
    if (c == '\\' && i + 1 < end) {
      const char next = in[i + 1];
      switch (next) {
        case '|':
          out.append("&#124;");
          break;
        case '^':
          out.append("&#94;");
          break;
        case '~':
          out.append("&#126;");
          break;
        // The spoiler pair tokens ">!" / "!<" are also assembled from
        // adjacent characters after md4c unescapes, so their pieces get the
        // same treatment.
        case '!':
          out.append("&#33;");
          break;
        case '<':
          out.append("&#60;");
          break;
        case '>':
          out.append("&#62;");
          break;
        default:
          // Copy escape pairs atomically so "\\|" reads as literal
          // backslash + active pipe.
          out.push_back(c);
          out.push_back(next);
          break;
      }
      i += 2;
      continue;
    }
    out.push_back(c);
    i++;
  }
}

} // namespace

std::string preprocessMarkdown(const std::string& input) {
  std::string out;
  out.reserve(input.size() + 8);

  bool inFence = false;
  size_t pos = 0;
  const size_t n = input.size();

  while (pos < n) {
    size_t lineEnd = input.find('\n', pos);
    if (lineEnd == std::string::npos) {
      lineEnd = n;
    }

    if (isFenceLine(input, pos, lineEnd)) {
      inFence = !inFence;
      out.append(input, pos, lineEnd - pos);
    } else if (inFence) {
      out.append(input, pos, lineEnd - pos);
    } else {
      // Skip up to 3 leading spaces; 4+ may be indented code -- leave alone.
      size_t i = pos;
      size_t spaces = 0;
      while (i < lineEnd && (input[i] == ' ' || input[i] == '\t') && spaces < 4) {
        if (input[i] == '\t') {
          spaces = 4;
        } else {
          spaces++;
        }
        i++;
      }
      bool escaped = false;
      if (spaces < 4) {
        // Walk any blockquote markers; escape the ">" that opens ">!".
        size_t j = i;
        while (j < lineEnd && input[j] == '>') {
          if (j + 1 < lineEnd && input[j + 1] == '!') {
            out.append(input, pos, j - pos);
            out.push_back('\\');
            appendWithEscapeRewrite(input, j, lineEnd, out);
            escaped = true;
            break;
          }
          j++;
          if (j < lineEnd && input[j] == ' ') {
            j++;
          }
        }
      }
      if (!escaped) {
        appendWithEscapeRewrite(input, pos, lineEnd, out);
      }
    }

    if (lineEnd < n) {
      out.push_back('\n');
    }
    pos = lineEnd + 1;
  }

  return out;
}

} // namespace jetmarkdown
