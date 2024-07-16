package com.android.systemui.biometrics

import android.content.Context
import android.hardware.biometrics.common.AuthenticateReason
import android.provider.Settings
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.util.settings.repository.UserAwareSecureSettingsRepository
import com.google.hardware.biometrics.parcelables.fingerprint.PressToAuthParcelable
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

private val TAG = "FITA_Google"

@SysUISingleton
class FingerprintInteractiveToAuthGoogleProviderImpl
@Inject constructor(
    private val context: Context,
    private val settings: UserAwareSecureSettingsRepository,
): FingerprintInteractiveToAuthProvider {

    private val defaultSettingValue = context.resources.getBoolean(com.android.internal.R.bool.config_performantAuthDefault)

    override val enabledForCurrentUser: Flow<Boolean>
        get() = settings.boolSettingForActiveUser(Settings.Secure.SFPS_PERFORMANT_AUTH_ENABLED, defaultSettingValue)

    override fun getVendorExtension(userId: Int): AuthenticateReason.Vendor {
        val setting = Settings.Secure.getIntForUser(context.contentResolver,
            Settings.Secure.SFPS_PERFORMANT_AUTH_ENABLED, if (defaultSettingValue) 1 else 0, userId)

        Log.d(TAG, "getVendorExtension, userId: $userId, SFPS_PERFORMANT_AUTH_ENABLED: $setting")

        return AuthenticateReason.Vendor().apply {
            extension.setParcelable(PressToAuthParcelable().apply {
                // setting can be -1 in some cases, which should be treated same as 0
                pressToAuthEnabled = setting <= 0
            })
        }
    }
}
