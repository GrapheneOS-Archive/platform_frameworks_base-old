package com.android.server.pm;

import android.Manifest;
import android.content.pm.PackageInstaller;
import android.os.IBinder;
import android.util.ArrayMap;

import java.util.List;

class PrivilegedInstallerHelper {
    private final PackageManagerService pm;

    PrivilegedInstallerHelper(PackageManagerService pm) {
        this.pm = pm;
    }

    // The purpose of this method is to provide a data structure that allows instances of privileged
    // installers across all users avoid installing the same package at the same time.
    //
    // Example:
    // Installer A in user #1 starts atomic installation of packages P1, P2, P3.
    // At the same time,
    // installer B in user #2 starts atomic installation of packages P2, P4, P5.
    // If both installers use this method to add their packages to the list of busy packages before
    // starting the installation process, it's guaranteed to return true in at most one of the
    // installers (it can return false in both if some of the packages are already busy).
    //
    // After installation completes, package names need to be manually removed from the list of busy
    // packages. If the installer process dies, packages that were added by it are removed from the
    // list automatically.
    //
    // Note that this approach doesn't handle installer process dying before installation completes
    // (after PackageInstaller session is committed, installation proceeds regardless of whether the
    // installer is still alive). Handling this would increase the complexity significantly.
    // In the vast majority of cases, installer remains alive for the whole duration of package
    // installation.

    private static final int BUSY_PACKAGES_MAX_NUM = 1000;
    private final ArrayMap<String, IBinder> busyPackages = new ArrayMap<>();

    boolean updateListOfBusyPackages(boolean add, // true is add, false is remove
                                     List<String> packageNames, IBinder callerBinder) {
        pm.mContext.enforceCallingPermission(Manifest.permission.INSTALL_PACKAGES, null);

        for (String packageName : packageNames) {
            if (packageName.length() > PackageInstaller.SessionParams.MAX_PACKAGE_NAME_LENGTH) {
                throw new IllegalArgumentException();
            }
        }

        ArrayMap<String, IBinder> busyPackages = this.busyPackages;

        synchronized (busyPackages) {
            // remove packages that were marked as busy by processes that no longer exist
            busyPackages.values().removeIf(e -> !e.pingBinder());

            if (add) {
                int remainingCapacity = BUSY_PACKAGES_MAX_NUM - busyPackages.size();

                if (remainingCapacity < packageNames.size()) {
                    return false;
                }

                // check that none of the packages are busy to ensure atomicity
                for (String packageName : packageNames) {
                    if (busyPackages.containsKey(packageName)) {
                        return false;
                    }
                }

                for (String packageName : packageNames) {
                    busyPackages.put(packageName, callerBinder);
                }

                return true;
            } else {
                // check that all packages were previously marked as busy by the caller
                for (String packageName : packageNames) {
                    if (!callerBinder.equals(busyPackages.get(packageName))) {
                        throw new IllegalStateException(packageName + " was not added by the caller");
                    }
                }

                for (String packageName : packageNames) {
                    busyPackages.remove(packageName);
                }

                return true;
            }
        }
    }
}
