package com.android.server.pm.pkg;

import android.annotation.SystemApi;
import android.processor.immutability.Immutable;

/** @hide */
@SystemApi(client = SystemApi.Client.SYSTEM_SERVER)
@Immutable
// Placeholder interface to comply with PackageUserState requirement of having custom return types
// be interfaces.
// TODO: consider adding getters for GosPackageState fields here
public interface GosPackageStatePmApi {
}
