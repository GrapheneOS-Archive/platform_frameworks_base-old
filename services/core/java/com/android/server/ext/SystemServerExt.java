/*
 * Copyright (C) 2022 GrapheneOS
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

package com.android.server.ext;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;

import com.android.internal.os.BackgroundThread;
import com.android.server.pm.PackageManagerService;

public final class SystemServerExt {

    public final Context context;
    public final Handler bgHandler;
    public final PackageManagerService packageManager;

    private SystemServerExt(Context systemContext, PackageManagerService pm) {
        context = systemContext;
        bgHandler = BackgroundThread.getHandler();
        packageManager = pm;
    }

    /*
     Called after system server has completed its initialization,
     but before any of the apps are started.

     Call from com.android.server.SystemServer#startOtherServices(), at the end of lambda
     that is passed into mActivityManagerService.systemReady()
     */
    public static void init(Context systemContext, PackageManagerService pm) {
        SystemServerExt sse = new SystemServerExt(systemContext, pm);
        sse.bgHandler.post(sse::initBgThread);
    }

    void initBgThread() {

    }

    public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter, Handler handler) {
        context.registerReceiver(receiver, filter, null, handler);
    }
}
