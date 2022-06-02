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

import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.app.admin.FactoryResetProtectionPolicy;
import android.app.admin.IDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

public class GmcDevicePolicyManager extends DevicePolicyManager {
    public GmcDevicePolicyManager(Context context, IDevicePolicyManager service) {
        super(context, service);
    }

    @Override
    public boolean isDeviceProvisioned() {
        return true;
    }

    @Override
    public @Nullable FactoryResetProtectionPolicy getFactoryResetProtectionPolicy(@Nullable ComponentName admin) {
        // called during account removal to check whether it's allowed, requires privileged permissions
        return null;
    }

    @Override
    public ComponentName getDeviceOwnerComponentOnAnyUser() {
        return null;
    }

    @Override
    public String getDeviceOwnerNameOnAnyUser() {
        return null;
    }

    @Override
    public @Nullable String getProfileOwnerNameAsUser(int userId) {
        return null;
    }
}
