/**
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 — GPL-3.0
 *
 * fialka_jni.cpp — JNI bridge to Monero wallet2 C++ API.
 *
 * Based on the structure of monerujo.cpp (m2049r, Apache-2.0) and
 * anonero.cpp (ANONERO project, Apache-2.0).
 *
 * KEY DESIGN DECISIONS vs old libmonero_jni.so (BUILD=16):
 *  - NO custom DIAG loop in setDaemon / init
 *  - NO blocking refresh passes at wallet open time
 *  - Wallet pointer returned as jlong handle — no hidden global state
 *  - Standard wallet2_api.h only — fully auditable, rebuild from source
 *  - wallet->init() is non-blocking; wallet->startRefresh() drives sync
 */

#include <inttypes.h>
#include "fialka_jni.h"
#include "wallet2_api.h"

#include <android/log.h>

// C++ linkage — must be defined outside extern "C"
std::mutex _listenerMutex;

#ifdef __cplusplus
extern "C" {
#endif
#define LOG_TAG "FialkaJNI"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,   LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,    LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,    LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   LOG_TAG, __VA_ARGS__)

static JavaVM *cachedJVM;
static jclass class_ArrayList;
static jclass class_WalletListener;
static jclass class_TransactionInfo;
static jclass class_Transfer;
static jclass class_WalletStatus;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cachedJVM = jvm;
    LOGI("JNI_OnLoad");
    JNIEnv *jenv;
    if (jvm->GetEnv(reinterpret_cast<void **>(&jenv), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    class_ArrayList = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("java/util/ArrayList")));
    class_TransactionInfo = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("com/fialkaapp/fialka/wallet/jni/TransactionInfo")));
    class_Transfer = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("com/fialkaapp/fialka/wallet/jni/Transfer")));
    class_WalletListener = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("com/fialkaapp/fialka/wallet/jni/WalletListener")));
    class_WalletStatus = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("com/fialkaapp/fialka/wallet/jni/WalletStatus")));
    return JNI_VERSION_1_6;
}

#ifdef __cplusplus
}
#endif

// ── JVM helpers ──────────────────────────────────────────────────────────────

int attachJVM(JNIEnv **jenv) {
    int envStat = cachedJVM->GetEnv((void **) jenv, JNI_VERSION_1_6);
    if (envStat == JNI_EDETACHED) {
        if (cachedJVM->AttachCurrentThread(jenv, nullptr) != 0) {
            LOGE("Failed to attach JVM");
            return JNI_ERR;
        }
    } else if (envStat == JNI_EVERSION) {
        LOGE("GetEnv: version not supported");
        return JNI_ERR;
    }
    return envStat;
}

void detachJVM(JNIEnv *jenv, int envStat) {
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
    }
    if (envStat == JNI_EDETACHED) {
        cachedJVM->DetachCurrentThread();
    }
}

// ── Handle helper ─────────────────────────────────────────────────────────────

template<typename T>
T *getHandle(JNIEnv *env, jobject obj, const char *fieldName = "handle") {
    jclass cls = env->GetObjectClass(obj);
    jfieldID fid = env->GetFieldID(cls, fieldName, "J");
    return reinterpret_cast<T *>(env->GetLongField(obj, fid));
}

// ── WalletListener ───────────────────────────────────────────────────────────

struct MyWalletListener : Monero::WalletListener {
    jobject jlistener;

    explicit MyWalletListener(JNIEnv *env, jobject aListener) {
        jlistener = env->NewGlobalRef(aListener);
    }

    ~MyWalletListener() override = default;

    void deleteGlobalJavaRef(JNIEnv *env) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        env->DeleteGlobalRef(jlistener);
        jlistener = nullptr;
    }

    void updated() override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;
        jmethodID mid = jenv->GetMethodID(class_WalletListener, "updated", "()V");
        jenv->CallVoidMethod(jlistener, mid);
        detachJVM(jenv, envStat);
    }

    void moneySpent(const std::string &txId, uint64_t amount) override {}

    void moneyReceived(const std::string &txId, uint64_t amount) override {}

    void unconfirmedMoneyReceived(const std::string &txId, uint64_t amount) override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;
        jstring jTxId = jenv->NewStringUTF(txId.c_str());
        jmethodID mid = jenv->GetMethodID(class_WalletListener,
                                          "unconfirmedMoneyReceived",
                                          "(Ljava/lang/String;J)V");
        jenv->CallVoidMethod(jlistener, mid, jTxId, (jlong) amount);
        jenv->DeleteLocalRef(jTxId);
        detachJVM(jenv, envStat);
    }

    void newBlock(uint64_t height) override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;
        jmethodID mid = jenv->GetMethodID(class_WalletListener, "newBlock", "(J)V");
        jenv->CallVoidMethod(jlistener, mid, (jlong) height);
        detachJVM(jenv, envStat);
    }

    void refreshed() override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;
        jmethodID mid = jenv->GetMethodID(class_WalletListener, "refreshed", "()V");
        jenv->CallVoidMethod(jlistener, mid);
        detachJVM(jenv, envStat);
    }
};

// ── JNI helpers ───────────────────────────────────────────────────────────────

jobject newWalletStatusInstance(JNIEnv *env, int status, const std::string &errorString) {
    jmethodID init = env->GetMethodID(class_WalletStatus, "<init>", "(ILjava/lang/String;)V");
    jstring jErr = env->NewStringUTF(errorString.c_str());
    jobject obj = env->NewObject(class_WalletStatus, init, status, jErr);
    env->DeleteLocalRef(jErr);
    return obj;
}

#ifdef __cplusplus
extern "C" {
#endif

/**********************************/
/********** WalletManager *********/
/**********************************/

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_createWalletJ(
        JNIEnv *env, jobject instance,
        jstring path, jstring password, jstring language, jint networkType) {
    const char *_path     = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr);
    Monero::NetworkType _net = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWallet(
                    std::string(_path), std::string(_password), std::string(_language), _net);
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(language, _language);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_openWalletJ(
        JNIEnv *env, jobject instance,
        jstring path, jstring password, jint networkType) {
    const char *_path     = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::NetworkType _net = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->openWallet(
                    std::string(_path), std::string(_password), _net);
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_recoveryWalletJ(
        JNIEnv *env, jobject instance,
        jstring path, jstring password,
        jstring mnemonic, jstring offset,
        jint networkType, jlong restoreHeight) {
    const char *_path      = env->GetStringUTFChars(path, nullptr);
    const char *_password  = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic  = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset    = env->GetStringUTFChars(offset, nullptr);
    Monero::NetworkType _net = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->recoveryWallet(
                    std::string(_path),
                    std::string(_password),
                    std::string(_mnemonic),
                    _net,
                    (uint64_t) restoreHeight,
                    1,   // kdf_rounds
                    std::string(_offset));
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_walletExists(
        JNIEnv *env, jobject instance, jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    bool exists = Monero::WalletManagerFactory::getWalletManager()->walletExists(std::string(_path));
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(exists);
}

JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_closeJ(
        JNIEnv *env, jobject instance, jobject walletInstance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, walletInstance);
    bool success = Monero::WalletManagerFactory::getWalletManager()->closeWallet(wallet, false);
    if (success) {
        MyWalletListener *listener = getHandle<MyWalletListener>(env, walletInstance, "listenerHandle");
        if (listener != nullptr) {
            listener->deleteGlobalJavaRef(env);
            delete listener;
        }
    }
    LOGD("wallet closed: %s", success ? "ok" : "fail");
    return static_cast<jboolean>(success);
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_getBlockchainHeight(
        JNIEnv *env, jobject instance) {
    return Monero::WalletManagerFactory::getWalletManager()->blockchainHeight();
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_getBlockchainTargetHeight(
        JNIEnv *env, jobject instance) {
    return Monero::WalletManagerFactory::getWalletManager()->blockchainTargetHeight();
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_setDaemonAddressJ(
        JNIEnv *env, jobject instance, jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    Monero::WalletManagerFactory::getWalletManager()->setDaemonAddress(std::string(_address));
    env->ReleaseStringUTFChars(address, _address);
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_initLogger(
        JNIEnv *env, jclass clazz, jstring argv0, jstring defaultLogBaseName) {
    const char *_argv0    = env->GetStringUTFChars(argv0, nullptr);
    const char *_logName  = env->GetStringUTFChars(defaultLogBaseName, nullptr);
    Monero::Wallet::init(_argv0, _logName);
    env->ReleaseStringUTFChars(argv0, _argv0);
    env->ReleaseStringUTFChars(defaultLogBaseName, _logName);
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_WalletManager_setLogLevel(
        JNIEnv *env, jclass clazz, jint level) {
    Monero::WalletManagerFactory::setLogLevel(level);
}

/**********************************/
/************ Wallet **************/
/**********************************/

JNIEXPORT jstring JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getSeed(
        JNIEnv *env, jobject instance, jstring seedOffset) {
    const char *_offset = env->GetStringUTFChars(seedOffset, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    jstring seed = env->NewStringUTF(wallet->seed(std::string(_offset)).c_str());
    env->ReleaseStringUTFChars(seedOffset, _offset);
    return seed;
}

JNIEXPORT jobject JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_statusWithErrorString(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    int status;
    std::string errorString;
    wallet->statusWithErrorString(status, errorString);
    return newWalletStatusInstance(env, status, errorString);
}

JNIEXPORT jstring JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getAddressJ(
        JNIEnv *env, jobject instance, jint accountIndex, jint addressIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(
            wallet->address((uint32_t) accountIndex, (uint32_t) addressIndex).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getSecretViewKey(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretViewKey().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getSecretSpendKey(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretSpendKey().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_store(
        JNIEnv *env, jobject instance, jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = wallet->store(std::string(_path));
    if (!success) {
        LOGE("store(): %s", wallet->errorString().c_str());
    }
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(success);
}

/**
 * init() — connects wallet to daemon. NON-BLOCKING. No DIAG loop. No refresh pass.
 * After this, call startRefresh() to begin background sync.
 */
JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_initJ(
        JNIEnv *env, jobject instance,
        jstring daemonAddress,
        jlong upperTransactionSizeLimit,
        jstring daemonUsername, jstring daemonPassword,
        jstring proxyAddress) {
    const char *_daemon    = env->GetStringUTFChars(daemonAddress, nullptr);
    const char *_user      = env->GetStringUTFChars(daemonUsername, nullptr);
    const char *_pass      = env->GetStringUTFChars(daemonPassword, nullptr);
    const char *_proxy     = env->GetStringUTFChars(proxyAddress, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    LOGI("initJ(): connecting to %s", _daemon);
    bool status = wallet->init(
            _daemon,
            (uint64_t) upperTransactionSizeLimit,
            _user, _pass,
            false, false,
            _proxy);
    LOGI("initJ(): done status=%d", (int) status);
    env->ReleaseStringUTFChars(daemonAddress, _daemon);
    env->ReleaseStringUTFChars(daemonUsername, _user);
    env->ReleaseStringUTFChars(daemonPassword, _pass);
    env->ReleaseStringUTFChars(proxyAddress, _proxy);
    return static_cast<jboolean>(status);
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_setRestoreHeight(
        JNIEnv *env, jobject instance, jlong height) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setRefreshFromBlockHeight((uint64_t) height);
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getRestoreHeight(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->getRefreshFromBlockHeight();
}

JNIEXPORT jint JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getConnectionStatusJ(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->connected();
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getBalance(
        JNIEnv *env, jobject instance, jint accountIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->balance((uint32_t) accountIndex);
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getUnlockedBalance(
        JNIEnv *env, jobject instance, jint accountIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->unlockedBalance((uint32_t) accountIndex);
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getBlockChainHeight(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->blockChainHeight();
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getDaemonBlockChainHeight(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->daemonBlockChainHeight();
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getDaemonBlockChainTargetHeight(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->daemonBlockChainTargetHeight();
}

JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_isSynchronizedJ(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->synchronized());
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_startRefresh(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->startRefresh();
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_pauseRefresh(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->pauseRefresh();
}

JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_refresh(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->refresh());
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_refreshAsync(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->refreshAsync();
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_rescanBlockchainAsyncJ(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->rescanBlockchainAsync();
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_setListenerJ(
        JNIEnv *env, jobject instance, jobject javaListener) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setListener(nullptr);
    MyWalletListener *oldListener = getHandle<MyWalletListener>(env, instance, "listenerHandle");
    if (oldListener != nullptr) {
        oldListener->deleteGlobalJavaRef(env);
        delete oldListener;
    }
    if (javaListener == nullptr) {
        return 0;
    }
    MyWalletListener *listener = new MyWalletListener(env, javaListener);
    wallet->setListener(listener);
    return reinterpret_cast<jlong>(listener);
}

JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_isAddressValid(
        JNIEnv *env, jclass clazz, jstring address, jint networkType) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    Monero::NetworkType _net = static_cast<Monero::NetworkType>(networkType);
    bool isValid = Monero::Wallet::addressValid(_address, _net);
    env->ReleaseStringUTFChars(address, _address);
    return static_cast<jboolean>(isValid);
}

JNIEXPORT jstring JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getDisplayAmount(
        JNIEnv *env, jclass clazz, jlong amount) {
    return env->NewStringUTF(Monero::Wallet::displayAmount(amount).c_str());
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getAmountFromString(
        JNIEnv *env, jclass clazz, jstring amount) {
    const char *_amount = env->GetStringUTFChars(amount, nullptr);
    uint64_t x = Monero::Wallet::amountFromString(_amount);
    env->ReleaseStringUTFChars(amount, _amount);
    return x;
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getMaximumAllowedAmount(
        JNIEnv *env, jclass clazz) {
    return Monero::Wallet::maximumAllowedAmount();
}

/**********************************/
/****** TransactionHistory ********/
/**********************************/

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_getHistoryJ(
        JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return reinterpret_cast<jlong>(wallet->history());
}

JNIEXPORT jint JNICALL
Java_com_fialkaapp_fialka_wallet_jni_TransactionHistory_getCount(
        JNIEnv *env, jobject instance) {
    Monero::TransactionHistory *h = getHandle<Monero::TransactionHistory>(env, instance);
    return h->count();
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_TransactionHistory_refreshJ(
        JNIEnv *env, jobject instance) {
    Monero::TransactionHistory *h = getHandle<Monero::TransactionHistory>(env, instance);
    h->refresh();
}

JNIEXPORT jobject JNICALL
Java_com_fialkaapp_fialka_wallet_jni_TransactionHistory_getAllJ(
        JNIEnv *env, jobject instance) {
    Monero::TransactionHistory *h = getHandle<Monero::TransactionHistory>(env, instance);
    h->refresh();

    jmethodID alInit  = env->GetMethodID(class_ArrayList, "<init>", "()V");
    jmethodID alAdd   = env->GetMethodID(class_ArrayList, "add", "(Ljava/lang/Object;)Z");
    jobject   list    = env->NewObject(class_ArrayList, alInit);

    jmethodID txInit  = env->GetMethodID(class_TransactionInfo, "<init>", "()V");

    jfieldID fDirection    = env->GetFieldID(class_TransactionInfo, "direction",       "I");
    jfieldID fIsPending    = env->GetFieldID(class_TransactionInfo, "isPending",       "Z");
    jfieldID fIsFailed     = env->GetFieldID(class_TransactionInfo, "isFailed",        "Z");
    jfieldID fAmount       = env->GetFieldID(class_TransactionInfo, "amount",          "J");
    jfieldID fFee          = env->GetFieldID(class_TransactionInfo, "fee",             "J");
    jfieldID fBlockHeight  = env->GetFieldID(class_TransactionInfo, "blockHeight",     "J");
    jfieldID fConfirms     = env->GetFieldID(class_TransactionInfo, "confirmations",   "J");
    jfieldID fTimestamp    = env->GetFieldID(class_TransactionInfo, "timestamp",       "J");
    jfieldID fTxId         = env->GetFieldID(class_TransactionInfo, "txId",            "Ljava/lang/String;");
    jfieldID fPaymentId    = env->GetFieldID(class_TransactionInfo, "paymentId",       "Ljava/lang/String;");
    jfieldID fLabel        = env->GetFieldID(class_TransactionInfo, "subaddressLabel", "Ljava/lang/String;");

    const std::vector<Monero::TransactionInfo *> &txs = h->getAll();
    for (Monero::TransactionInfo *tx : txs) {
        jobject obj = env->NewObject(class_TransactionInfo, txInit);

        env->SetIntField(obj,     fDirection,   (jint) tx->direction());
        env->SetBooleanField(obj, fIsPending,   (jboolean) tx->isPending());
        env->SetBooleanField(obj, fIsFailed,    (jboolean) tx->isFailed());
        env->SetLongField(obj,    fAmount,      (jlong) tx->amount());
        env->SetLongField(obj,    fFee,         (jlong) tx->fee());
        env->SetLongField(obj,    fBlockHeight, (jlong) tx->blockHeight());
        env->SetLongField(obj,    fConfirms,    (jlong) tx->confirmations());
        env->SetLongField(obj,    fTimestamp,   (jlong) tx->timestamp());

        jstring jTxId  = env->NewStringUTF(tx->hash().c_str());
        jstring jPayId = env->NewStringUTF(tx->paymentId().c_str());
        jstring jLabel = env->NewStringUTF(tx->label().c_str());
        env->SetObjectField(obj, fTxId,      jTxId);
        env->SetObjectField(obj, fPaymentId, jPayId);
        env->SetObjectField(obj, fLabel,     jLabel);
        env->DeleteLocalRef(jTxId);
        env->DeleteLocalRef(jPayId);
        env->DeleteLocalRef(jLabel);

        env->CallBooleanMethod(list, alAdd, obj);
        env->DeleteLocalRef(obj);
    }
    return list;
}

/**********************************/
/****** PendingTransaction ********/
/**********************************/

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_createTransactionJ(
        JNIEnv *env, jobject instance,
        jstring dstAddr, jstring paymentId,
        jlong amount, jint mixinCount,
        jint priority, jint accountIndex) {
    const char *_dst       = env->GetStringUTFChars(dstAddr, nullptr);
    const char *_paymentId = env->GetStringUTFChars(paymentId, nullptr);
    Monero::PendingTransaction::Priority _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    Monero::PendingTransaction *tx = wallet->createTransaction(
            _dst, _paymentId, (uint64_t) amount,
            (uint32_t) mixinCount, _priority, (uint32_t) accountIndex);
    env->ReleaseStringUTFChars(dstAddr, _dst);
    env->ReleaseStringUTFChars(paymentId, _paymentId);
    return reinterpret_cast<jlong>(tx);
}

JNIEXPORT jint JNICALL
Java_com_fialkaapp_fialka_wallet_jni_PendingTransaction_getStatusJ(
        JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx->status();
}

JNIEXPORT jstring JNICALL
Java_com_fialkaapp_fialka_wallet_jni_PendingTransaction_getErrorString(
        JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return env->NewStringUTF(tx->errorString().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_fialkaapp_fialka_wallet_jni_PendingTransaction_commit(
        JNIEnv *env, jobject instance, jstring filename, jboolean overwrite) {
    const char *_filename = env->GetStringUTFChars(filename, nullptr);
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    bool success = tx->commit(_filename, overwrite);
    env->ReleaseStringUTFChars(filename, _filename);
    return static_cast<jboolean>(success);
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_PendingTransaction_getAmount(
        JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return static_cast<jlong>(tx->amount());
}

JNIEXPORT jlong JNICALL
Java_com_fialkaapp_fialka_wallet_jni_PendingTransaction_getFee(
        JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return static_cast<jlong>(tx->fee());
}

JNIEXPORT jstring JNICALL
Java_com_fialkaapp_fialka_wallet_jni_PendingTransaction_getFirstTxIdJ(
        JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    std::vector<std::string> txids = tx->txid();
    if (!txids.empty())
        return env->NewStringUTF(txids.front().c_str());
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_fialkaapp_fialka_wallet_jni_Wallet_disposeTransaction(
        JNIEnv *env, jobject instance, jobject pendingTransaction) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, pendingTransaction);
    wallet->disposeTransaction(tx);
}

#ifdef __cplusplus
}
#endif
