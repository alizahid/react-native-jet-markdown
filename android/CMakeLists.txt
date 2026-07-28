# Supersedes the codegen-generated CMakeLists (react-native.config.js points
# autolinking here) so the library can compile the shared C++ core and swap
# the codegen component descriptor for the custom measurable shadow node.

cmake_minimum_required(VERSION 3.13)
set(CMAKE_VERBOSE_MAKEFILE on)

set(lib_android_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(lib_cpp_DIR ${lib_android_DIR}/../cpp)
set(lib_codegen_DIR ${lib_android_DIR}/build/generated/source/codegen/jni)

file(GLOB codegen_SRCS CONFIGURE_DEPENDS
  ${lib_codegen_DIR}/*.cpp
  ${lib_codegen_DIR}/react/renderer/components/JetMarkdownViewSpec/*.cpp)
# The custom descriptor in cpp/react/override replaces the generated one.
list(FILTER codegen_SRCS EXCLUDE REGEX "ComponentDescriptors\\.cpp$")

file(GLOB lib_SRCS CONFIGURE_DEPENDS
  ${lib_cpp_DIR}/core/*.cpp
  ${lib_cpp_DIR}/react/*.cpp)

# md4c is C; keep it out of the C++-flagged reactnative target.
add_library(jetmarkdown_md4c STATIC ${lib_cpp_DIR}/md4c/md4c.c)
set_target_properties(jetmarkdown_md4c PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(
  react_codegen_JetMarkdownViewSpec
  OBJECT
  ${codegen_SRCS}
  ${lib_SRCS}
  ${lib_android_DIR}/src/main/cpp/JniBindings.cpp
)

# The override directory must come first so
# <react/renderer/components/JetMarkdownViewSpec/ComponentDescriptors.h>
# resolves to the custom descriptor everywhere (autolinking included).
target_include_directories(react_codegen_JetMarkdownViewSpec PUBLIC
  ${lib_cpp_DIR}/react/override
  ${lib_codegen_DIR}
  ${lib_codegen_DIR}/react/renderer/components/JetMarkdownViewSpec
  ${lib_cpp_DIR}
)

target_link_libraries(
  react_codegen_JetMarkdownViewSpec
  jetmarkdown_md4c
  fbjni
  jsi
  reactnative
)

target_compile_reactnative_options(react_codegen_JetMarkdownViewSpec PRIVATE)
