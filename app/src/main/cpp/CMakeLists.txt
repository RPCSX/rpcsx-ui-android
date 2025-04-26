cmake_minimum_required(VERSION 3.10)
project("rpcsx-android")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE on)

if (CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64")
	add_subdirectory(libadrenotools)
else()
	add_library(adrenotools INTERFACE)
endif()

add_library(${CMAKE_PROJECT_NAME} SHARED native-lib.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    adrenotools
)
