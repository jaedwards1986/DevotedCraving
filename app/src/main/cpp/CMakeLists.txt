cmake_minimum_required(VERSION 3.22.1)
project(forgeai_jni LANGUAGES C CXX)

include(FetchContent)

# Reproducible upstream source pin. Do not replace with a moving branch/tag.
set(FORGEAI_LLAMA_CPP_COMMIT "c74c71a6f7a0b06df30f979520e5d9a86a4d60ad" CACHE STRING "Pinned llama.cpp commit")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
endif()

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG ${FORGEAI_LLAMA_CPP_COMMIT}
    GIT_SHALLOW FALSE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(forgeai_jni SHARED native-lib.cpp)
target_compile_features(forgeai_jni PRIVATE cxx_std_17)
target_compile_options(forgeai_jni PRIVATE -Wall -Wextra -Werror)
target_link_libraries(forgeai_jni PRIVATE llama log)
