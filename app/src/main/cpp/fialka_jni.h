/**
 * fialka_jni.h — declarations for fialka_jni.cpp
 */
#pragma once
#include <jni.h>
#include <mutex>
#include <string>
#include <vector>

extern std::mutex _listenerMutex;
int attachJVM(JNIEnv **jenv);
void detachJVM(JNIEnv *jenv, int envStat);
