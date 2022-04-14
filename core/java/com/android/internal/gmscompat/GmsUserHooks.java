/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.gmscompat;

import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.Collections;
import java.util.List;

/**
 * GMS tries to interact across user profiles, which requires privileged permissions.
 * As a workaround, a pseudo-single-user environment is constructed by hiding non-current users
 * and marking the current user as the primary ("Owner") user.
 */
public class GmsUserHooks {

    private static int getUserId() {
        return UserHandle.myUserId();
    }

    private static void checkUserId(int userId) {
        if (userId != getUserId()) {
            throw new IllegalStateException("unexpected userId " + userId);
        }
    }

    private static int getUserSerialNumber() {
        // GMS has several hardcoded (userSerialNumber == 0) checks
        return 0;
    }

    private static UserInfo getUserInfo() {
        // obtaining UserInfo is a privileged operation (even for the current user)
        UserInfo ui = new UserInfo();
        ui.id = getUserId();
        ui.serialNumber = getUserSerialNumber();
        // "system" means "primary" ("Owner") user
        ui.userType = UserManager.USER_TYPE_FULL_SYSTEM;
        ui.flags = UserInfo.FLAG_SYSTEM | UserInfo.FLAG_FULL;
        return ui;
    }

    // ActivityManager#getCurrentUser()
    public static int getCurrentUser() {
        return getUserId();
    }

    // ActivityManager#isUserRunning(int)
    public static boolean isUserRunning(int userId) {
        checkUserId(userId);
        return true;
    }

    // UserManager#getUserInfo(int)
    public static UserInfo getUserInfo(int userId) {
        checkUserId(userId);
        return getUserInfo();
    }

    // UserManager#getUserHandle(int)
    public static int getUserHandle(int userSerialNumber) {
        if (userSerialNumber != getUserSerialNumber()) {
            throw new IllegalStateException("unexpected userSerialNumber " + userSerialNumber);
        }
        return getUserId();
    }

    // UserManager#getUsers(boolean, boolean, boolean)
    public static List<UserInfo> getUsers() {
        return Collections.singletonList(getUserInfo());
    }

    // UserManager#getUserSerialNumber(int)
    public static int getUserSerialNumber(int userId) {
        checkUserId(userId);
        return getUserSerialNumber();
    }

    // getProfile*() shims to support the managed ("work") profiles

    // UserManager#getProfileParent(int)
    public static UserInfo getProfileParent(int userId) {
        checkUserId(userId);
        return null;
    }

    // UserManager#getProfiles(int)
    public static List<UserInfo> getProfiles(int userId) {
        checkUserId(userId);
        return getUsers();
    }

    // UserManager#getProfileIds(int, boolean)
    public static int[] getProfileIds(int userId) {
        checkUserId(userId);
        return new int[] { userId };
    }

    private GmsUserHooks() {}
}
