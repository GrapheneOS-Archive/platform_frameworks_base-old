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

package com.android.internal.gmscompat.client;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

/*
The sole purpose of this service is to provide a way to move a client of GMS Core
out of the background state by binding to it from a foreground process.

Privileged GMS Core achieves this by sending a privileged broadcast with
BroadcastOptions#setTemporaryAppAllowlist() option set.

A declaration of this service is added to all clients of GMS Core during package parsing,
see GmsClientHooks#maybeAddServiceDuringParsing()
 */
public class GmsCompatClientService extends Service {
    private static final Binder dummyBinder = new Binder();

    @Override
    public IBinder onBind(Intent intent) {
        return dummyBinder;
    }
}
