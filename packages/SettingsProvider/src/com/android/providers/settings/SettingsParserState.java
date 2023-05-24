package com.android.providers.settings;

import android.provider.Settings;
import android.util.Log;

class SettingsParserState {
    private final int type;

    SettingsParserState(int type) {
        this.type = type;
    }

    // Return false to discard the setting
    boolean onSettingRead(String key, String val) {
        if (key == null) {
            return true;
        }

        switch (type) {
            case SettingsState.SETTINGS_TYPE_GLOBAL: {
                switch (key) {
                    default:
                        break;
                }
                break;
            }
            case SettingsState.SETTINGS_TYPE_SECURE: {
                switch (key) {
                    default:
                        break;
                }
                break;
            }
        }

        return true;
    }

    void onFinish() {
        switch (type) {
            case SettingsState.SETTINGS_TYPE_GLOBAL: {
                return;
            }
            case SettingsState.SETTINGS_TYPE_SECURE: {
                return;
            }
        }
    }
}
