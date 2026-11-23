cmake_minimum_required(VERSION 3.22.1)
project(forgeai_jni LANGUAGES CXX)

add_library(
    forgeai_jni
    SHARED
    native-lib.cpp
)

target_compile_features(forgeai_jni PRIVATE cxx_std_17)
target_compile_options(forgeai_jni PRIVATE -Wall -Wextra -Werror)
