#define DISCORDPP_IMPLEMENTATION
#include "discord_bridge.h"

#include <android/log.h>
#include <cstring>

#define LOG_TAG "DiscordBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

DiscordBridge g_discordBridge;

DiscordBridge::DiscordBridge()
    : client_(nullptr), ready_(false), authorized_(false),
      javaVm_(nullptr), managerClass_(nullptr), statusMethod_(nullptr) {}

DiscordBridge::~DiscordBridge() = default;

bool DiscordBridge::Init(int64_t appId, JavaVM* vm, jclass managerClass, jmethodID statusMethod) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (client_) {
        delete client_;
        client_ = nullptr;
    }
    ready_ = false;
    authorized_ = false;
    javaVm_ = vm;
    managerClass_ = managerClass;
    statusMethod_ = statusMethod;

    try {
        client_ = new discordpp::Client();
        client_->SetApplicationId(static_cast<uint64_t>(appId));
        client_->SetStatusChangedCallback(
            [this](discordpp::Client::Status status, discordpp::Client::Error error, int32_t detail) {
                bool ready;
                bool authorized;
                {
                    std::lock_guard<std::mutex> lk(mutex_);
                    if (status == discordpp::Client::Status::Ready) {
                        ready_ = true;
                    } else if (status == discordpp::Client::Status::Disconnected) {
                        ready_ = false;
                    }
                    ready = ready_;
                    authorized = authorized_;
                }
                LOGI("Status changed: status=%d error=%d detail=%d ready=%d",
                     static_cast<int>(status), static_cast<int>(error), detail, ready ? 1 : 0);
                // Called without the lock held: Kotlin may call straight back into the bridge.
                FireStatusChanged(static_cast<int>(status), ready, authorized);
            });
        return true;
    } catch (const std::exception& e) {
        LOGE("Init failed: %s", e.what());
    } catch (...) {
        LOGE("Init failed with an unknown exception");
    }
    delete client_;
    client_ = nullptr;
    return false;
}

void DiscordBridge::SetTokenAndConnect(const char* token) {
    if (!token) return;
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) {
        LOGW("SetTokenAndConnect: no client");
        return;
    }
    try {
        client_->UpdateToken(
            discordpp::AuthorizationTokenType::Bearer,
            std::string(token),
            [this](discordpp::ClientResult result) {
                if (!result.Successful()) {
                    LOGE("UpdateToken failed: %s (%d)", result.Error().c_str(), result.ErrorCode());
                    return;
                }
                std::lock_guard<std::mutex> lk(mutex_);
                authorized_ = true;
                if (client_ && !ready_) {
                    client_->Connect();
                }
            });
    } catch (const std::exception& e) {
        LOGE("SetTokenAndConnect threw: %s", e.what());
    } catch (...) {
        LOGE("SetTokenAndConnect threw an unknown exception");
    }
}

void DiscordBridge::SetActivity(
    int activityType,
    const char* name, const char* state, const char* details,
    int64_t startSecs, int64_t endSecs,
    const char* largeImage, const char* largeText,
    const char* smallImage, const char* smallText,
    const char* button1Label, const char* button1Url,
    const char* button2Label, const char* button2Url
) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_ || !ready_) {
        LOGW("SetActivity: client is not ready");
        return;
    }
    try {
        discordpp::Activity activity;
        activity.SetType(static_cast<discordpp::ActivityTypes>(activityType));
        if (name) activity.SetName(std::string(name));
        if (state) activity.SetState(std::string(state));
        if (details) activity.SetDetails(std::string(details));

        if (startSecs > 0 || endSecs > 0) {
            discordpp::ActivityTimestamps ts;
            if (startSecs > 0) ts.SetStart(static_cast<uint64_t>(startSecs));
            if (endSecs > 0) ts.SetEnd(static_cast<uint64_t>(endSecs));
            activity.SetTimestamps(std::move(ts));
        }

        discordpp::ActivityAssets assets;
        if (largeImage) assets.SetLargeImage(std::string(largeImage));
        if (largeText) assets.SetLargeText(std::string(largeText));
        if (smallImage) assets.SetSmallImage(std::string(smallImage));
        if (smallText) assets.SetSmallText(std::string(smallText));
        activity.SetAssets(std::move(assets));

        if (button1Label && button1Url && strlen(button1Label) > 0 && strlen(button1Url) > 0) {
            discordpp::ActivityButton button;
            button.SetLabel(std::string(button1Label));
            button.SetUrl(std::string(button1Url));
            activity.AddButton(std::move(button));
        }
        if (button2Label && button2Url && strlen(button2Label) > 0 && strlen(button2Url) > 0) {
            discordpp::ActivityButton button;
            button.SetLabel(std::string(button2Label));
            button.SetUrl(std::string(button2Url));
            activity.AddButton(std::move(button));
        }

        client_->UpdateRichPresence(std::move(activity), [](discordpp::ClientResult result) {
            if (!result.Successful()) {
                LOGE("UpdateRichPresence failed: %s (%d)", result.Error().c_str(), result.ErrorCode());
            }
        });
    } catch (const std::exception& e) {
        LOGE("SetActivity threw: %s", e.what());
    } catch (...) {
        LOGE("SetActivity threw an unknown exception");
    }
}

void DiscordBridge::SetOnlineStatus(int statusType) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_ || !ready_) return;
    try {
        client_->SetOnlineStatus(
            static_cast<discordpp::StatusType>(statusType),
            [](discordpp::ClientResult result) {
                if (!result.Successful()) {
                    LOGE("SetOnlineStatus failed: %s (%d)", result.Error().c_str(), result.ErrorCode());
                }
            });
    } catch (const std::exception& e) {
        LOGE("SetOnlineStatus threw: %s", e.what());
    } catch (...) {
        LOGE("SetOnlineStatus threw an unknown exception");
    }
}

/** Current discordpp::StatusType of the signed-in user, or -1 when it can't be read. */
int DiscordBridge::GetOnlineStatus() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_ || !ready_) return -1;
    try {
        auto user = client_->GetCurrentUserV2();
        if (!user) return -1;
        return static_cast<int>(user->Status());
    } catch (const std::exception& e) {
        LOGW("GetOnlineStatus threw: %s", e.what());
    } catch (...) {
        LOGW("GetOnlineStatus threw an unknown exception");
    }
    return -1;
}

void DiscordBridge::Clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_ || !ready_) return;
    try {
        client_->UpdateRichPresence(discordpp::Activity(), [](discordpp::ClientResult result) {
            if (!result.Successful()) {
                LOGE("Clear failed: %s (%d)", result.Error().c_str(), result.ErrorCode());
            }
        });
    } catch (const std::exception& e) {
        LOGE("Clear threw: %s", e.what());
    } catch (...) {
        LOGE("Clear threw an unknown exception");
    }
}

void DiscordBridge::Disconnect() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_) return;
    try {
        client_->Disconnect();
    } catch (const std::exception& e) {
        LOGE("Disconnect threw: %s", e.what());
    } catch (...) {
        LOGE("Disconnect threw an unknown exception");
    }
    ready_ = false;
    authorized_ = false;
}

void DiscordBridge::DestroyLocked(JNIEnv* env) {
    ready_ = false;
    authorized_ = false;
    if (client_) {
        try {
            client_->Disconnect();
        } catch (...) {
            LOGW("Destroy: Disconnect threw (ignored)");
        }
        delete client_;
        client_ = nullptr;
    }
    if (env && managerClass_) {
        env->DeleteGlobalRef(managerClass_);
    }
    managerClass_ = nullptr;
    statusMethod_ = nullptr;
    javaVm_ = nullptr;
}

void DiscordBridge::Destroy() {
    JNIEnv* env = nullptr;
    JavaVM* vm;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        vm = javaVm_;
    }
    bool attached = false;
    if (vm && vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            attached = true;
        } else {
            env = nullptr;
        }
    }
    {
        std::lock_guard<std::mutex> lock(mutex_);
        DestroyLocked(env);
    }
    if (attached) {
        vm->DetachCurrentThread();
    }
}

void DiscordBridge::RunCallbacks() {
    try {
        discordpp::RunCallbacks();
    } catch (const std::exception& e) {
        LOGE("RunCallbacks threw: %s", e.what());
    } catch (...) {
        LOGE("RunCallbacks threw an unknown exception");
    }
}

void DiscordBridge::FireStatusChanged(int statusCode, bool ready, bool authorized) {
    JavaVM* vm;
    jclass cls;
    jmethodID method;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        vm = javaVm_;
        cls = managerClass_;
        method = statusMethod_;
    }
    if (!vm || !cls || !method) return;

    JNIEnv* env = nullptr;
    bool attached = false;
    int envStatus = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (envStatus == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, const_cast<char*>("DiscordCallback"), nullptr};
        if (vm->AttachCurrentThread(&env, &args) != JNI_OK) return;
        attached = true;
    } else if (envStatus != JNI_OK) {
        return;
    }
    env->CallStaticVoidMethod(
        cls, method, static_cast<jint>(statusCode),
        static_cast<jboolean>(ready), static_cast<jboolean>(authorized));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (attached) {
        vm->DetachCurrentThread();
    }
}

namespace {

/** Copies a Java string into UTF-8 for the duration of a call; null stays null. */
class JString {
public:
    JString(JNIEnv* env, jstring value) : env_(env), value_(value), chars_(nullptr) {
        if (value_) chars_ = env_->GetStringUTFChars(value_, nullptr);
    }
    ~JString() {
        if (chars_) env_->ReleaseStringUTFChars(value_, chars_);
    }
    JString(const JString&) = delete;
    JString& operator=(const JString&) = delete;
    const char* get() const { return chars_; }

private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_;
};

} // namespace

#define JNI_FN(name) Java_org_koitharu_kotatsu_scrobbling_discord_data_DiscordRpcManager_##name

extern "C" {

JNIEXPORT jboolean JNICALL JNI_FN(nativeInit)(JNIEnv* env, jobject /*thiz*/, jlong appId) {
    JavaVM* vm = nullptr;
    if (env->GetJavaVM(&vm) != JNI_OK) return JNI_FALSE;

    jclass local = env->FindClass("org/koitharu/kotatsu/scrobbling/discord/data/DiscordRpcManager");
    if (!local) return JNI_FALSE;
    jclass global = static_cast<jclass>(env->NewGlobalRef(local));
    jmethodID method = env->GetStaticMethodID(local, "onNativeStatusChanged", "(IZZ)V");
    env->DeleteLocalRef(local);
    if (!global || !method) {
        if (global) env->DeleteGlobalRef(global);
        return JNI_FALSE;
    }
    if (!g_discordBridge.Init(static_cast<int64_t>(appId), vm, global, method)) {
        env->DeleteGlobalRef(global);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL JNI_FN(nativeSetTokenAndConnect)(JNIEnv* env, jobject /*thiz*/, jstring token) {
    JString value(env, token);
    g_discordBridge.SetTokenAndConnect(value.get());
}

JNIEXPORT void JNICALL JNI_FN(nativeSetActivity)(
    JNIEnv* env, jobject /*thiz*/,
    jint activityType,
    jstring name, jstring state, jstring details,
    jlong startSecs, jlong endSecs,
    jstring largeImage, jstring largeText,
    jstring smallImage, jstring smallText,
    jstring button1Label, jstring button1Url,
    jstring button2Label, jstring button2Url
) {
    JString cName(env, name), cState(env, state), cDetails(env, details);
    JString cLargeImage(env, largeImage), cLargeText(env, largeText);
    JString cSmallImage(env, smallImage), cSmallText(env, smallText);
    JString cButton1Label(env, button1Label), cButton1Url(env, button1Url);
    JString cButton2Label(env, button2Label), cButton2Url(env, button2Url);
    g_discordBridge.SetActivity(
        static_cast<int>(activityType),
        cName.get(), cState.get(), cDetails.get(),
        static_cast<int64_t>(startSecs), static_cast<int64_t>(endSecs),
        cLargeImage.get(), cLargeText.get(), cSmallImage.get(), cSmallText.get(),
        cButton1Label.get(), cButton1Url.get(), cButton2Label.get(), cButton2Url.get());
}

JNIEXPORT void JNICALL JNI_FN(nativeSetOnlineStatus)(JNIEnv* /*env*/, jobject /*thiz*/, jint statusType) {
    g_discordBridge.SetOnlineStatus(static_cast<int>(statusType));
}

JNIEXPORT jint JNICALL JNI_FN(nativeGetOnlineStatus)(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(g_discordBridge.GetOnlineStatus());
}

JNIEXPORT void JNICALL JNI_FN(nativeClear)(JNIEnv* /*env*/, jobject /*thiz*/) {
    g_discordBridge.Clear();
}

JNIEXPORT void JNICALL JNI_FN(nativeDisconnect)(JNIEnv* /*env*/, jobject /*thiz*/) {
    g_discordBridge.Disconnect();
}

JNIEXPORT void JNICALL JNI_FN(nativeRunCallbacks)(JNIEnv* /*env*/, jobject /*thiz*/) {
    g_discordBridge.RunCallbacks();
}

JNIEXPORT void JNICALL JNI_FN(nativeDestroy)(JNIEnv* /*env*/, jobject /*thiz*/) {
    g_discordBridge.Destroy();
}

} // extern "C"
