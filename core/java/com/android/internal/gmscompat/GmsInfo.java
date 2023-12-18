/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.ext.PackageId;

/** @hide */
public final class GmsInfo {
    // Package names for GMS apps
    public static final String PACKAGE_GSF = PackageId.GSF_NAME; // "Google Services Framework"
    public static final String PACKAGE_GMS_CORE = PackageId.GMS_CORE_NAME; // "Play services"
    public static final String PACKAGE_PLAY_STORE = PackageId.PLAY_STORE_NAME;

    // "Google" app. "GSA" (G Search App) is its internal name
    public static final String PACKAGE_GSA = PackageId.G_SEARCH_APP_NAME;

    // Used for restricting accessibility of exported components, reducing the scope of broadcasts, etc.
    // Held by GSF, GmsCore, Play Store.
    public static final String SIGNATURE_PROTECTED_PERMISSION = "com.google.android.providers.gsf.permission.WRITE_GSERVICES";
}
