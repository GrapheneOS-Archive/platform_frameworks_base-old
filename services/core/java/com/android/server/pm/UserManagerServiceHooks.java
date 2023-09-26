package com.android.server.pm;

import android.os.Bundle;
import android.os.UserManager;
import android.util.TypedXmlPullParser;

import android.annotation.NonNull;

public class UserManagerServiceHooks {

    public static void updateDefaultRestrictionsIfNecessary(TypedXmlPullParser parser, @NonNull Bundle bundle) {
        // Check if the attribute is present in XML
        int index = parser.getAttributeIndex(null, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
        if (index == -1) {
            bundle.putBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, true);
        }
    }
}
