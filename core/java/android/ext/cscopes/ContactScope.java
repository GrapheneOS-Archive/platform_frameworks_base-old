package android.ext.cscopes;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
@SystemApi
public final class ContactScope implements Parcelable {
    public static final int TYPE_GROUP = 0;
    public static final int TYPE_CONTACT = 1;
    public static final int TYPE_NUMBER = 2;
    public static final int TYPE_EMAIL = 3;
    public static final int TYPE_COUNT = 4;

    public final int type;
    public final long id;

    @Nullable
    public final String title;
    @Nullable
    public final String summary;
    @Nullable
    public final Uri detailsUri;

    public ContactScope(int type, long id, @Nullable String title, @Nullable String summary, @Nullable Uri detailsUri) {
        this.type = type;
        this.id = id;
        this.title = title;
        this.summary = summary;
        this.detailsUri = detailsUri;
    }

    ContactScope(Parcel in) {
        type = in.readInt();
        id = in.readLong();
        title = in.readString();
        summary = in.readString();
        detailsUri = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(type);
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(summary);
        dest.writeParcelable(detailsUri, 0);
    }

    @NonNull
    public static final Creator<ContactScope> CREATOR = new Creator<>() {
        @Override
        public ContactScope createFromParcel(Parcel in) {
            return new ContactScope(in);
        }

        @Override
        public ContactScope[] newArray(int size) {
            return new ContactScope[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
