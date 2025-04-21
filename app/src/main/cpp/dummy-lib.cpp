#include <jni.h>
#include <string>

extern "C" {
    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_initialize(JNIEnv *, jobject, jstring) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_installFw(JNIEnv *, jobject, jint, jlong) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_install(JNIEnv *, jobject, jint, jlong) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_installKey(JNIEnv *, jobject, jint, jlong, jstring) {
        return JNI_TRUE;
    }

    JNIEXPORT jint JNICALL
    Java_net_rpcsx_RPCSX_boot(JNIEnv *, jobject, jstring) {
        return 0; // BootResult.NoErrors.ordinal
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_surfaceEvent(JNIEnv *, jobject, jobject, jint) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_usbDeviceEvent(JNIEnv *, jobject, jint, jint, jint, jint) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_processCompilationQueue(JNIEnv *, jobject) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_startMainThreadProcessor(JNIEnv *, jobject) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_overlayPadData(JNIEnv *, jobject, jint, jint, jint, jint, jint, jint) {
        return JNI_TRUE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_collectGameInfo(JNIEnv *, jobject, jstring, jlong) {
        return JNI_TRUE;
    }

    JNIEXPORT jstring JNICALL
    Java_net_rpcsx_RPCSX_systemInfo(JNIEnv *env, jobject) {
        return env->NewStringUTF("Dummy System Info v1.0");
    }

    JNIEXPORT jstring JNICALL
    Java_net_rpcsx_RPCSX_settingsGet(JNIEnv *env, jobject, jstring) {
        return env->NewStringUTF("{\"dummy_setting_key\":\"dummy_setting_value\"}");
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_settingsSet(JNIEnv *, jobject, jstring, jstring) {
        return JNI_TRUE;
    }

    JNIEXPORT jint JNICALL
    Java_net_rpcsx_RPCSX_getState(JNIEnv *, jobject) {
        return 6; // EmulatorState.Ready.ordinal
    }

    JNIEXPORT void JNICALL
    Java_net_rpcsx_RPCSX_kill(JNIEnv *, jobject) {}

    JNIEXPORT void JNICALL
    Java_net_rpcsx_RPCSX_resume(JNIEnv *, jobject) {}

    JNIEXPORT void JNICALL
    Java_net_rpcsx_RPCSX_openHomeMenu(JNIEnv *, jobject) {}

    JNIEXPORT jstring JNICALL
    Java_net_rpcsx_RPCSX_getTitleId(JNIEnv *env, jobject) {
        return env->NewStringUTF("DUMMY00001");
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_supportsCustomDriverLoading(JNIEnv *, jobject) {
        return JNI_FALSE;
    }

    JNIEXPORT jboolean JNICALL
    Java_net_rpcsx_RPCSX_isInstallableFile(JNIEnv *, jobject, jint) {
        return JNI_FALSE;
    }

    JNIEXPORT jstring JNICALL
    Java_net_rpcsx_RPCSX_getDirInstallPath(JNIEnv *env, jobject, jint) {
        return env->NewStringUTF("/dummy/path/");
    }

    JNIEXPORT jstring JNICALL
    Java_net_rpcsx_RPCSX_getVersion(JNIEnv *env, jobject) {
        return env->NewStringUTF("0.0.1-dummy");
    }
}