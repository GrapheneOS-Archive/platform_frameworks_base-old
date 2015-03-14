/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.internal.os;

import dalvik.system.VMRuntime;
import android.system.ErrnoException;
import android.system.Os;

/**
 * Startup class for the process.
 * @hide
 */
public class ExecInit {
    /**
     * Class not instantiable.
     */
    private ExecInit() {
    }

    /**
     * The main function called when starting a runtime application.
     *
     * The first argument is the target SDK version for the app.
     *
     * The remaining arguments are passed to the runtime.
     *
     * @param args The command-line arguments.
     */
    public static void main(String[] args) {
        try {
            // Parse our mandatory argument.
            int targetSdkVersion = Integer.parseInt(args[0], 10);

            // Launch the application.
            String[] runtimeArgs = new String[args.length - 1];
            System.arraycopy(args, 1, runtimeArgs, 0, runtimeArgs.length);
            RuntimeInit.execInit(targetSdkVersion, runtimeArgs);
        } catch (ZygoteInit.MethodAndArgsCaller caller) {
            caller.run();
        }
    }

    /**
     * Executes a runtime application.
     * This method never returns.
     *
     * @param niceName The nice name for the application, or null if none.
     * @param targetSdkVersion The target SDK version for the app.
     * @param args Arguments for {@link RuntimeInit#main}.
     */
    public static void execApplication(String niceName, int targetSdkVersion,
            String instructionSet, String[] args) {
        int niceArgs = niceName == null ? 0 : 1;
        int baseArgs = 5 + niceArgs;
        String[] argv = new String[baseArgs + args.length];
        if (VMRuntime.is64BitInstructionSet(instructionSet)) {
            argv[0] = "/system/bin/app_process64";
        } else {
            argv[0] = "/system/bin/app_process32";
        }
        argv[1] = "/system/bin";
        argv[2] = "--application";
        if (niceName != null) {
            argv[3] = "--nice-name=" + niceName;
        }
        argv[3 + niceArgs] = "com.android.internal.os.ExecInit";
        argv[4 + niceArgs] = Integer.toString(targetSdkVersion);
        System.arraycopy(args, 0, argv, baseArgs, args.length);

        try {
            Os.execv(argv[0], argv);
        } catch (ErrnoException e) {
            throw new RuntimeException(e);
        }
    }
}
