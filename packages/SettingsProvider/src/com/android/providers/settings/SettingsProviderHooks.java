package com.android.providers.settings;

import android.os.UserHandle;
import android.provider.Settings;

class SettingsProviderHooks {

    static void onSettingsStateInit(final SettingsProvider.SettingsRegistry registry, final int userId) {
        if (userId == UserHandle.USER_SYSTEM) {
            SettingsState globalSettings = registry.getSettingsLocked(SettingsProvider.SETTINGS_TYPE_GLOBAL, userId);
            insertSetting(globalSettings, Settings.Global.ADD_USERS_WHEN_LOCKED, "0" /* disabled value */);
            insertSetting(globalSettings, Settings.Global.ENABLE_EPHEMERAL_FEATURE, "0" /* disabled value */);
        }
        SettingsState secureSettings = registry.getSettingsLocked(SettingsProvider.SETTINGS_TYPE_SECURE, userId);
    }

    /**
     * Unlike UpgradeController#upgradeIfNeededLocked settings migration, this runs every time a user is initialized.
     * Insert or modify setting upon SettingState initialization for any user, or in case of system user, upon boot.
     */
    private static void insertSetting(SettingsState state, String key, String value) {
        state.insertSettingLocked(key, value, null /* tag */, false /* makeDefault */, SettingsState.SYSTEM_PACKAGE_NAME);
    }
}
