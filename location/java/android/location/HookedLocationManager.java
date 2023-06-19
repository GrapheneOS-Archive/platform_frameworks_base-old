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
    private static int flags;

    public static int getFlags(GosPackageStateBase gosPs, boolean isUserApp) {
        return 0;
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

    @Override
    public boolean isProviderEnabled(@NonNull String provider) {
        return super.isProviderEnabled(provider);
    }

    @Nullable
    @Override
    public ProviderProperties getProviderProperties(@NonNull String provider) {
        return super.getProviderProperties(provider);
    }

    @Nullable
    @Override
    public Location getLastKnownLocation(@NonNull String provider,
                                         @NonNull LastLocationRequest lastLocationRequest) {
        return super.getLastKnownLocation(provider, lastLocationRequest);
    }

    @Override
    public void getCurrentLocation(@NonNull String provider, @NonNull LocationRequest locationRequest,
                                   @Nullable CancellationSignal cancellationSignal,
                                   @NonNull Executor executor, @NonNull Consumer<Location> consumer) {
        super.getCurrentLocation(provider, locationRequest, cancellationSignal, executor, consumer);
    }

    @Override
    public void requestLocationUpdates(@NonNull String provider, @NonNull LocationRequest locationRequest,
                                       @NonNull Executor executor, @NonNull LocationListener listener) {
        super.requestLocationUpdates(provider, locationRequest, executor, listener);
    }

    @Override
    public void requestLocationUpdates(@NonNull String provider, @NonNull LocationRequest locationRequest,
                                       @NonNull PendingIntent pendingIntent) {
        super.requestLocationUpdates(provider, locationRequest, pendingIntent);
    }

    @Override
    public void requestFlush(@NonNull String provider, @NonNull LocationListener listener, int requestCode) {
        super.requestFlush(provider, listener, requestCode);
    }

    @Override
    public void requestFlush(@NonNull String provider, @NonNull PendingIntent pendingIntent, int requestCode) {
        super.requestFlush(provider, pendingIntent, requestCode);
    }

    @Override
    public void addTestProvider(@NonNull String provider, @NonNull ProviderProperties properties,
                                @NonNull Set<String> extraAttributionTags) {
        super.addTestProvider(provider, properties, extraAttributionTags);
    }

    @Override
    public void removeTestProvider(@NonNull String provider) {
        super.removeTestProvider(provider);
    }

    @Override
    public void setTestProviderEnabled(@NonNull String provider, boolean enabled) {
        super.setTestProviderEnabled(provider, enabled);
    }

    @Override
    public void setTestProviderLocation(@NonNull String provider, @NonNull Location location) {
        super.setTestProviderLocation(provider, location);
    }
}
