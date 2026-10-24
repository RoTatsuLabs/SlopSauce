cmake_minimum_required(VERSION 3.22)
project("slopsauce_discord")

# The Discord Social SDK ships as an AAR (app/libs/discord_partner_sdk.aar) with a prefab
# package, which supplies discordpp.h and the shared library.
find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(
        slopsauce_discord
        SHARED
        discord_bridge.cpp
)

# Fallback for a toolchain that does not expose the prefab headers: copy them from the AAR
# (prefab/modules/discord_partner_sdk/include) into ./include.
target_include_directories(
        slopsauce_discord
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

target_link_libraries(
        slopsauce_discord
        discord_partner_sdk::discord_partner_sdk
        log
)

target_compile_features(
        slopsauce_discord
        PUBLIC
        cxx_std_17
)
