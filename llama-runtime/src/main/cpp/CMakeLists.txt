cmake_minimum_required(VERSION 3.31.6)
project(nexora_llama_runtime LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "NEXORA currently builds the native runtime for arm64-v8a only.")
endif()

set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp source missing at ${LLAMA_SRC}. The CI workflow must fetch the pinned source before Gradle builds.")
endif()

add_subdirectory("${LLAMA_SRC}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")

add_library(ai-chat SHARED ai_chat.cpp)
target_include_directories(ai-chat PRIVATE
    "${LLAMA_SRC}"
    "${LLAMA_SRC}/common"
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/ggml/include"
    "${LLAMA_SRC}/ggml/src"
)
target_link_libraries(ai-chat PRIVATE llama llama-common android log)
