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

package com.android.internal.gmscompat.dynamite.client;

import android.util.jar.StrictJarFile;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/** @hide */
public final class ModuleLoadState {
    private static final Pattern CLASSES_DEX_PATTERN = Pattern.compile("^classes\\d*\\.dex$");

    public String modulePath;
    public FileDescriptor moduleFd;

    public ModuleLoadState(String modulePath, FileDescriptor moduleFd) {
        this.modulePath = modulePath;
        // Do NOT close the original fd. The Bionic linker could dup it for library loading
        // at any time. Unfortunately, this results in CloseGuard warnings, but it's more efficient
        // to just ignore them.
        this.moduleFd = moduleFd;
    }

    public ByteBuffer[] mapDexBuffers() throws IOException {
        // Native code doesn't assume ownership, so we can safely use the original fd temporarily
        FileChannel channel = new FileInputStream(moduleFd).getChannel();
        // Dynamite modules don't seem to have proper v2 signatures, so don't verify them
        StrictJarFile jar = new StrictJarFile(moduleFd, false, false);

        ArrayList<ByteBuffer> buffers = new ArrayList<>(1);
        jar.iterator().forEachRemaining(entry -> {
            if (entry.getMethod() == ZipEntry.STORED && CLASSES_DEX_PATTERN.matcher(entry.getName()).matches()) {
                try {
                    buffers.add(channel.map(FileChannel.MapMode.READ_ONLY, entry.getDataOffset(), entry.getSize()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return buffers.toArray(new ByteBuffer[0]);
    }
}
