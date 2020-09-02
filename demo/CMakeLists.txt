# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        apm-remote-data-observer

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        src/main/jni/jni_RemoteDataObserver.cpp
        src/main/jni/remote_data_observer.cpp
        )

#add_library(
#        HDACEngine
#        SHARED
#        IMPORTED
#)
#
#add_library(
#        agora-rtc-sdk
#        SHARED
#        IMPORTED
#)
#
#add_library(
#        agora-rtc-sdk-jni
#        SHARED
#        IMPORTED
#)
#
#add_library(
#        agora-crypto
#        SHARED
#        IMPORTED
#)
#
#add_library(
#        videoprp
#        SHARED
#        IMPORTED
#)

add_library(
        yuv_static
        STATIC
        IMPORTED
)

if (${ANDROID_ABI} STREQUAL "armeabi")
#    set_target_properties(
#            agora-av
#            PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/armeabi/libHDACEngine.so
#    )
#
#    set_target_properties(
#            agora-rtc
#            PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/armeabi/libagora-rtc-sdk-jni.so
#    )

    set_target_properties(
            yuv_static
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/armeabi/libyuv_static.a
    )
endif (${ANDROID_ABI} STREQUAL "armeabi")

if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
#    set_target_properties(
#            agora-av
#            PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/armeabi-v7a/libHDACEngine.so
#    )
#
#    set_target_properties(
#            agora-rtc
#            PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/armeabi-v7a/libagora-rtc-sdk-jni.so
#    )

    set_target_properties(
            yuv_static
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/armeabi-v7a/libyuv_static.a
    )
endif (${ANDROID_ABI} STREQUAL "armeabi-v7a")

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
#    set_target_properties(
#            agora-av
#            PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/arm64-v8a/libHDACEngine.so
#    )
#
#    set_target_properties(
#            agora-rtc
#            PROPERTIES IMPORTED_LOCATION
#            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/arm64-v8a/libagora-rtc-sdk-jni.so
#    )

    set_target_properties(
            yuv_static
            PROPERTIES IMPORTED_LOCATION
            ${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/arm64-v8a/libyuv_static.a
    )
endif (${ANDROID_ABI} STREQUAL "arm64-v8a")

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/include

)
# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        apm-remote-data-observer
#        agora-av
#        agora-rtc
        yuv_static
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})
