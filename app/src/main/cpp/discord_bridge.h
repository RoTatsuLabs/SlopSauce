#pragma once

#include <jni.h>
#include <mutex>
#include <string>

#include "discordpp.h"

/**
 * Wraps a single discordpp::Client and exposes it to Kotlin over JNI. One process-wide instance
 * (g_discordBridge), matching DiscordRpcManager's single-account design.
 *
 * Authorization (OAuth2 PKCE) is done on the Kotlin side; the bridge only receives the resulting
 * access token, connects, and publishes Rich Presence and the online status.
 */
class DiscordBridge {
public:
    DiscordBridge();
    ~DiscordBridge();

    bool Init(int64_t appId, JavaVM* vm, jclass managerClass, jmethodID statusMethod);
    void SetTokenAndConnect(const char* token);
    void SetActivity(
        int activityType,
        const char* name, const char* state, const char* details,
        int64_t startSecs, int64_t endSecs,
        const char* largeImage, const char* largeText,
        const char* smallImage, const char* smallText,
        const char* button1Label, const char* button1Url,
        const char* button2Label, const char* button2Url
    );
    void SetOnlineStatus(int statusType);
    int GetOnlineStatus();
    void Clear();
    void Disconnect();
    void Destroy();
    void RunCallbacks();

private:
    void DestroyLocked(JNIEnv* env);
    void FireStatusChanged(int statusCode, bool ready, bool authorized);

    std::mutex mutex_;
    discordpp::Client* client_;
    bool ready_;
    bool authorized_;
    JavaVM* javaVm_;
    jclass managerClass_;
    jmethodID statusMethod_;
};

extern DiscordBridge g_discordBridge;
