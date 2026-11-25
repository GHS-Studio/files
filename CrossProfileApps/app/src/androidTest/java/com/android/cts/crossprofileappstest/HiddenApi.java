/*
 * CrossProfileApps#startActivity(ComponentName, UserHandle) 在 Android 10 中是 @SystemApi
 * （隐藏 API），不在公开的 android-29 android.jar 里。CTS 原版在 AOSP 全量框架下编译可直接
 * 引用；独立 Gradle 工程改为反射调用，保证对公开 SDK 编译通过、运行时（instrumentation/shell
 * 身份）仍能访问该隐藏方法。
 */
package com.android.cts.crossprofileappstest;

import android.content.ComponentName;
import android.content.pm.CrossProfileApps;
import android.os.UserHandle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class HiddenApi {

    private HiddenApi() {
    }

    public static void startActivity(CrossProfileApps cpa,
                                     ComponentName component,
                                     UserHandle targetUser) {
        try {
            Method m = CrossProfileApps.class.getMethod(
                    "startActivity", ComponentName.class, UserHandle.class);
            m.invoke(cpa, component, targetUser);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new RuntimeException(t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
