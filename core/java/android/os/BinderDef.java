package android.os;

import android.annotation.Nullable;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;

import java.lang.reflect.Constructor;

import dalvik.system.DexClassLoader;

/**
 * Definition of a binder in another Android package.
 *
 * @hide
 */
public class BinderDef implements Parcelable {
    private static final String TAG = BinderDef.class.getSimpleName();

    public final String interfaceName; // also referred to as "interface descriptor"
    public final String apkPath;
    public final String className;
    // Sorted array of handled binder transactions codes, null means "all transactions are handled"
    @Nullable public final int[] transactionCodes;

    public BinderDef(String interfaceName, String apkPath, String className, @Nullable int[] transactionCodes) {
        this.interfaceName = interfaceName;
        this.apkPath = apkPath;
        this.className = className;
        this.transactionCodes = transactionCodes;
    }

    protected BinderDef(Parcel in) {
        interfaceName = in.readString();
        apkPath = in.readString();
        className = in.readString();
        transactionCodes = in.createIntArray();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(interfaceName);
        dest.writeString(apkPath);
        dest.writeString(className);
        dest.writeIntArray(transactionCodes);
    }

    private volatile IBinder instance;

    @Nullable
    public IBinder getInstance(Context ctx) {
        { IBinder cache = instance; if (cache != null) return cache; }

        synchronized (this) {
            { IBinder cache = instance; if (cache != null) return cache; }
            try {
                return instance = instantiate(ctx);
            } catch (ReflectiveOperationException e) {
                Log.e(TAG, "unable to instantiate " + className + " from " + apkPath, e);
                return null;
            }
        }
    }

    private IBinder instantiate(Context ctx) throws ReflectiveOperationException {
        Class cls = getDexClassLoader().loadClass(className);
        Constructor constructor = cls.getConstructor(Context.class);

        return (IBinder) constructor.newInstance(ctx);
    }

    private static final ArrayMap<String, DexClassLoader> classLoaders = new ArrayMap<>();

    private DexClassLoader getDexClassLoader() {
        final var map = classLoaders;
        synchronized (map) {
            DexClassLoader cl = map.get(apkPath);
            if (cl == null) {
                cl = new DexClassLoader(apkPath, null, null, String.class.getClassLoader());
                map.put(apkPath, cl);
            }
            return cl;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BinderDef> CREATOR = new Creator<BinderDef>() {
        @Override
        public BinderDef createFromParcel(Parcel in) {
            return new BinderDef(in);
        }

        @Override
        public BinderDef[] newArray(int size) {
            return new BinderDef[size];
        }
    };
}
