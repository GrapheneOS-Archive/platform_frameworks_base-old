package android.location;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.GosPackageStateBase;
import android.location.provider.ProviderProperties;
import android.os.CancellationSignal;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** @hide */
public class HookedLocationManager extends LocationManager {

    private static final int FLAG_ENABLE_PROVIDER_TRANSLATION = 1;

    private static int flags;

    public static int getFlags(GosPackageStateBase gosPs, boolean isUserApp) {
        if (isUserApp) {
            // see comment in translateProvider()
            return FLAG_ENABLE_PROVIDER_TRANSLATION;
        } else {
            return 0;
        }
    }

    public static void setFlags(int v) {
        flags = v;
    }

    public static boolean isEnabled() {
        return flags != 0;
    }

    public HookedLocationManager(@NonNull Context context, @NonNull ILocationManager service) {
        super(context, service);
    }

    private String translateProvider(String provider) {
        if ((flags & FLAG_ENABLE_PROVIDER_TRANSLATION) == 0) {
            return provider;
        }

        // There are apps that try to use NETWORK_PROVIDER even when it's not present in the list
        // returned by getAllProviders(), which leads to a crash.
        // As a workaround, redirect NETWORK_PROVIDER calls to an available provider
        if (NETWORK_PROVIDER.equals(provider)) {
            // getAllProviders() list is not guaranteed to be static, don't cache it
            List<String> allProviders = getAllProviders();
            if (allProviders.contains(NETWORK_PROVIDER)) {
                return NETWORK_PROVIDER;
            }
            if (allProviders.contains(GPS_PROVIDER)) {
                return GPS_PROVIDER;
            }
            if (allProviders.contains(FUSED_PROVIDER)) {
                return FUSED_PROVIDER;
            }
            // PASSIVE_PROVIDER is always present
            return PASSIVE_PROVIDER;
        }
        return provider;
    }

    private boolean isMissingProvider(String provider) {
        // getAllProviders() list is not guaranteed to be static, don't cache it
        return !getAllProviders().contains(provider);
    }

    @Override
    public boolean isProviderEnabled(@NonNull String provider) {
        if (isMissingProvider(provider)) {
            return false;
        }

        return super.isProviderEnabled(provider);
    }

    @Nullable
    @Override
    public ProviderProperties getProviderProperties(@NonNull String provider) {
        provider = translateProvider(provider);

        return super.getProviderProperties(provider);
    }

    @Nullable
    @Override
    public Location getLastKnownLocation(@NonNull String provider,
                                         @NonNull LastLocationRequest lastLocationRequest) {
        provider = translateProvider(provider);

        return super.getLastKnownLocation(provider, lastLocationRequest);
    }

    @Override
    public void getCurrentLocation(@NonNull String provider, @NonNull LocationRequest locationRequest,
                                   @Nullable CancellationSignal cancellationSignal,
                                   @NonNull Executor executor, @NonNull Consumer<Location> consumer) {
        provider = translateProvider(provider);

        super.getCurrentLocation(provider, locationRequest, cancellationSignal, executor, consumer);
    }

    @Override
    public void requestLocationUpdates(@NonNull String provider, @NonNull LocationRequest locationRequest,
                                       @NonNull Executor executor, @NonNull LocationListener listener) {
        provider = translateProvider(provider);

        super.requestLocationUpdates(provider, locationRequest, executor, listener);
    }

    @Override
    public void requestLocationUpdates(@NonNull String provider, @NonNull LocationRequest locationRequest,
                                       @NonNull PendingIntent pendingIntent) {
        provider = translateProvider(provider);

        super.requestLocationUpdates(provider, locationRequest, pendingIntent);
    }

    @Override
    public void requestFlush(@NonNull String provider, @NonNull LocationListener listener, int requestCode) {
        provider = translateProvider(provider);

        super.requestFlush(provider, listener, requestCode);
    }

    @Override
    public void requestFlush(@NonNull String provider, @NonNull PendingIntent pendingIntent, int requestCode) {
        provider = translateProvider(provider);

        super.requestFlush(provider, pendingIntent, requestCode);
    }


    // Test providers do not work properly for missing underlying providers, stub them out

    @Override
    public void addTestProvider(@NonNull String provider, @NonNull ProviderProperties properties,
                                @NonNull Set<String> extraAttributionTags) {
        if (isMissingProvider(provider)) {
            return;
        }

        super.addTestProvider(provider, properties, extraAttributionTags);
    }

    @Override
    public void removeTestProvider(@NonNull String provider) {
        if (isMissingProvider(provider)) {
            return;
        }

        super.removeTestProvider(provider);
    }

    @Override
    public void setTestProviderEnabled(@NonNull String provider, boolean enabled) {
        if (isMissingProvider(provider)) {
            return;
        }

        super.setTestProviderEnabled(provider, enabled);
    }

    @Override
    public void setTestProviderLocation(@NonNull String provider, @NonNull Location location) {
        if (isMissingProvider(provider)) {
            return;
        }

        super.setTestProviderLocation(provider, location);
    }
}
