package com.android.providers.settings;

import android.ext.settings.ConnChecksSetting;
import android.provider.Settings;
import android.util.Log;

class SettingsParserState {
    private final int type;

    private String captivePortalHttpsUrl;
    private String captivePortalMode;

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
                    case Settings.Global.CAPTIVE_PORTAL_HTTPS_URL:
                        captivePortalHttpsUrl = val;
                        // fallthrough
                    case Settings.Global.CAPTIVE_PORTAL_HTTP_URL:
                    case Settings.Global.CAPTIVE_PORTAL_FALLBACK_URL:
                    case Settings.Global.CAPTIVE_PORTAL_OTHER_FALLBACK_URLS:
                        // skip legacy captive portal detection URLs, remember one of them for
                        // migration in onFinish()
                        return false;
                    case Settings.Global.CAPTIVE_PORTAL_MODE:
                        // checked during migration of connectivity checks setting
                        captivePortalMode = val;
                        return false;
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
                maybeMigrateConnChecksSetting();
                return;
            }
            case SettingsState.SETTINGS_TYPE_SECURE: {
                return;
            }
        }
    }

    private void maybeMigrateConnChecksSetting() {
        if (ConnChecksSetting.isSet()) {
            return;
        }

        final int val;
        boolean connChecksDisabled = Integer.toString(Settings.Global.CAPTIVE_PORTAL_MODE_IGNORE)
                .equals(captivePortalMode);

        if (connChecksDisabled) {
            val = ConnChecksSetting.VAL_DISABLED;
        } else {
            if ("https://www.google.com/generate_204".equals(captivePortalHttpsUrl)) {
                val = ConnChecksSetting.VAL_STANDARD;
            } else {
                val = ConnChecksSetting.VAL_GRAPHENEOS;
            }
        }

        ConnChecksSetting.put(val);
    }
}
