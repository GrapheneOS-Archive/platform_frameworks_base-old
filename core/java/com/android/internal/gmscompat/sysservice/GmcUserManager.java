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

package com.android.internal.gmscompat.sysservice;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.IUserManager;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.Collections;
import java.util.List;

/**
 * GMS tries to interact across user profiles, which requires privileged permissions.
 * As a workaround, a pseudo-single-user environment is constructed by hiding non-current users
 * and marking the current user as the primary ("Owner") user.
 */
public class GmcUserManager extends UserManager {
    public GmcUserManager(Context context, IUserManager service) {
        super(context, service);
    }

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

    @Override
    public boolean isSystemUser() {
        return true;
    }

    @Override
    public UserInfo getUserInfo(int userId) {
        checkUserId(userId);
        return getUserInfo();
    }

    @Override
    public boolean hasBaseUserRestriction(String restrictionKey, UserHandle userHandle) {
        // Can't ignore device policy restrictions without permission
        return hasUserRestriction(restrictionKey, userHandle);
    }

    @Override
    public List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated) {
        return Collections.singletonList(getUserInfo());
    }

    @Override
    public int getUserSerialNumber(@UserIdInt int userId) {
        checkUserId(userId);
        return getUserSerialNumber();
    }

    @Override
    public @UserIdInt int getUserHandle(int userSerialNumber) {
        if (userSerialNumber != getUserSerialNumber()) {
            throw new IllegalStateException("unexpected userSerialNumber " + userSerialNumber);
        }
        return getUserId();
    }

    // ActivityManager#getCurrentUser()
    public static int amGetCurrentUser() {
        return getUserId();
    }

    // ActivityManager#isUserRunning(int)
    public static boolean amIsUserRunning(int userId) {
        checkUserId(userId);
        return true;
    }

    // support for managed ("work") profiles

    @Override
    public List<UserInfo> getProfiles(@UserIdInt int userId) {
        checkUserId(userId);
        return getUsers();
    }

    @Override
    public int[] getProfileIds(@UserIdInt int userId, boolean enabledOnly) {
        checkUserId(userId);
        return new int[] { userId };
    }

    @Override
    public UserInfo getProfileParent(int userId) {
        checkUserId(userId);
        return null;
    }

    @Override
    public boolean isManagedProfile() {
        return false;
    }
}
