// JNI surface for com.jetmarkdown.JetMarkdownNative. This TU is linked
// into the app's libappmodules.so (which owns JNI_OnLoad), so natives are
// exported by name instead of registered from an OnLoad hook.
//
// Strings cross as UTF-8 byte arrays: JNI's NewStringUTF/GetStringUTFChars
// speak modified UTF-8 and corrupt supplementary characters (emoji).

#include <jni.h>
#include <pthread.h>

#include <string>
#include <vector>

#include "core/AstSerializer.h"
#include "core/EditorRuns.h"
#include "core/Parser.h"
#include "react/JetMarkdownMeasurer.h"

namespace {

JavaVM* g_vm = nullptr;
jobject g_measurer = nullptr;
jmethodID g_measureMethod = nullptr;

std::string toStdString(JNIEnv* env, jbyteArray value) {
  if (value == nullptr) {
    return {};
  }
  const jsize length = env->GetArrayLength(value);
  std::string result(static_cast<size_t>(length), '\0');
  if (length > 0) {
    env->GetByteArrayRegion(value, 0, length, reinterpret_cast<jbyte*>(result.data()));
  }
  return result;
}

jbyteArray toByteArray(JNIEnv* env, const uint8_t* data, size_t size) {
  jbyteArray result = env->NewByteArray(static_cast<jsize>(size));
  if (result != nullptr && size > 0) {
    env->SetByteArrayRegion(
        result, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(data));
  }
  return result;
}

// Threads WE attach must detach before exiting or ART aborts the process
// ("Native thread exiting without having called DetachCurrentThread"); a
// pthread key destructor covers early exits.
pthread_key_t g_detachKey;
pthread_once_t g_detachKeyOnce = PTHREAD_ONCE_INIT;

void detachCurrentThread(void*) {
  if (g_vm != nullptr) {
    g_vm->DetachCurrentThread();
  }
}

void makeDetachKey() {
  pthread_key_create(&g_detachKey, detachCurrentThread);
}

JNIEnv* currentEnv() {
  if (g_vm == nullptr) {
    return nullptr;
  }
  JNIEnv* env = nullptr;
  const jint state = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
  if (state == JNI_EDETACHED) {
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
      return nullptr;
    }
    pthread_once(&g_detachKeyOnce, makeDetachKey);
    pthread_setspecific(g_detachKey, env);
  } else if (state != JNI_OK) {
    return nullptr;
  }
  return env;
}

} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_jetmarkdown_JetMarkdownNative_parse(JNIEnv* env, jclass, jbyteArray markdown) {
  const std::string input = toStdString(env, markdown);
  const auto document = jetmarkdown::parseMarkdown(input);
  const std::vector<uint8_t> bytes = jetmarkdown::serializeAst(document->root);
  return toByteArray(env, bytes.data(), bytes.size());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_jetmarkdown_JetMarkdownNative_markdownFromEditorContent(
    JNIEnv* env,
    jclass,
    jbyteArray text,
    jintArray runs,
    jintArray lineBlocks,
    jintArray linkRanges,
    jbyteArray linkUrls) {
  std::vector<jetmarkdown::StyledRun> styledRuns;
  if (runs != nullptr) {
    const jsize length = env->GetArrayLength(runs);
    std::vector<jint> values(static_cast<size_t>(length));
    if (length > 0) {
      env->GetIntArrayRegion(runs, 0, length, values.data());
    }
    for (jsize i = 0; i + 2 < length; i += 3) {
      styledRuns.push_back(
          {static_cast<uint32_t>(values[i]),
           static_cast<uint32_t>(values[i + 1]),
           static_cast<uint32_t>(values[i + 2])});
    }
  }
  std::vector<jetmarkdown::EditorLine> lines;
  if (lineBlocks != nullptr) {
    const jsize length = env->GetArrayLength(lineBlocks);
    std::vector<jint> values(static_cast<size_t>(length));
    if (length > 0) {
      env->GetIntArrayRegion(lineBlocks, 0, length, values.data());
    }
    for (jsize i = 0; i + 1 < length; i += 2) {
      lines.push_back(
          {static_cast<jetmarkdown::EditorBlockType>(values[i]),
           static_cast<uint8_t>(values[i + 1])});
    }
  }
  // Link URLs cross as one newline-joined blob (URLs cannot contain '\n').
  std::vector<jetmarkdown::LinkRun> links;
  if (linkRanges != nullptr) {
    const jsize length = env->GetArrayLength(linkRanges);
    std::vector<jint> values(static_cast<size_t>(length));
    if (length > 0) {
      env->GetIntArrayRegion(linkRanges, 0, length, values.data());
    }
    const std::string urls = toStdString(env, linkUrls);
    size_t urlStart = 0;
    for (jsize i = 0; i + 1 < length; i += 2) {
      const size_t urlEnd = urls.find('\n', urlStart);
      links.push_back(
          {static_cast<uint32_t>(values[i]),
           static_cast<uint32_t>(values[i + 1]),
           urls.substr(urlStart, urlEnd == std::string::npos ? std::string::npos
                                                             : urlEnd - urlStart)});
      urlStart = urlEnd == std::string::npos ? urls.size() : urlEnd + 1;
    }
  }
  const std::string result = jetmarkdown::markdownFromEditor(
      toStdString(env, text), styledRuns, lines, links);
  return toByteArray(
      env, reinterpret_cast<const uint8_t*>(result.data()), result.size());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_jetmarkdown_JetMarkdownNative_editorFromMarkdownContent(
    JNIEnv* env,
    jclass,
    jbyteArray markdown) {
  const jetmarkdown::EditorDocument document =
      jetmarkdown::editorFromMarkdown(toStdString(env, markdown));
  // [int32 runCount][runCount x (start, end, flags)][int32 lineCount]
  // [lineCount x (type, level)][utf8 text], little-endian.
  std::vector<uint8_t> bytes;
  bytes.reserve(
      8 + document.runs.size() * 12 + document.lines.size() * 8 +
      document.text.size());
  const auto push32 = [&bytes](uint32_t value) {
    bytes.push_back(static_cast<uint8_t>(value & 0xFF));
    bytes.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
    bytes.push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
    bytes.push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
  };
  push32(static_cast<uint32_t>(document.runs.size()));
  for (const jetmarkdown::StyledRun& run : document.runs) {
    push32(run.start);
    push32(run.end);
    push32(run.flags);
  }
  push32(static_cast<uint32_t>(document.lines.size()));
  for (const jetmarkdown::EditorLine& line : document.lines) {
    push32(static_cast<uint32_t>(line.type));
    push32(line.level);
  }
  push32(static_cast<uint32_t>(document.links.size()));
  for (const jetmarkdown::LinkRun& link : document.links) {
    push32(link.start);
    push32(link.end);
    push32(static_cast<uint32_t>(link.url.size()));
  }
  for (const jetmarkdown::LinkRun& link : document.links) {
    bytes.insert(bytes.end(), link.url.begin(), link.url.end());
  }
  bytes.insert(bytes.end(), document.text.begin(), document.text.end());
  return toByteArray(env, bytes.data(), bytes.size());
}

extern "C" JNIEXPORT void JNICALL
Java_com_jetmarkdown_JetMarkdownNative_installMeasurer(
    JNIEnv* env,
    jclass,
    jobject measurer) {
  env->GetJavaVM(&g_vm);
  if (g_measurer != nullptr) {
    env->DeleteGlobalRef(g_measurer);
  }
  g_measurer = env->NewGlobalRef(measurer);

  jclass measurerClass = env->GetObjectClass(measurer);
  g_measureMethod = env->GetMethodID(measurerClass, "measure", "([B[B[BFF)F");
  env->DeleteLocalRef(measurerClass);

  jetmarkdown::JetMarkdownMeasurer::shared().install(
      [](const std::string& markdown,
         const std::string& stylesJson,
         const std::string& imagesJson,
         float maxWidth,
         float fontScale) -> float {
        JNIEnv* jniEnv = currentEnv();
        if (jniEnv == nullptr || g_measurer == nullptr || g_measureMethod == nullptr) {
          return 0.0f;
        }
        jbyteArray jMarkdown = toByteArray(
            jniEnv, reinterpret_cast<const uint8_t*>(markdown.data()), markdown.size());
        jbyteArray jStyles = toByteArray(
            jniEnv, reinterpret_cast<const uint8_t*>(stylesJson.data()), stylesJson.size());
        jbyteArray jImages = toByteArray(
            jniEnv, reinterpret_cast<const uint8_t*>(imagesJson.data()), imagesJson.size());
        if (jMarkdown == nullptr || jStyles == nullptr || jImages == nullptr) {
          // NewByteArray OOM leaves a pending exception; calling into Java
          // with one pending is undefined behavior.
          jniEnv->ExceptionClear();
          return 0.0f;
        }
        const jfloat height = jniEnv->CallFloatMethod(
            g_measurer, g_measureMethod, jMarkdown, jStyles, jImages, maxWidth, fontScale);
        jniEnv->DeleteLocalRef(jMarkdown);
        jniEnv->DeleteLocalRef(jStyles);
        jniEnv->DeleteLocalRef(jImages);
        if (jniEnv->ExceptionCheck()) {
          jniEnv->ExceptionClear();
          return 0.0f;
        }
        return static_cast<float>(height);
      });
}
