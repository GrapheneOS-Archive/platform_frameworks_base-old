package android.ext.cscopes;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/** @hide */
@SystemApi
public final class ContactsGroup implements Parcelable {
    public final long id;
    @Nullable
    public final String title;
    @Nullable
    public final String summary;

    public ContactsGroup(long id, @Nullable String title, @Nullable String summary) {
        this.id = id;
        this.title = title;
        this.summary = summary;
    }

    ContactsGroup(@NonNull Parcel in) {
        id = in.readLong();
        title = in.readString();
        summary = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(summary);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<ContactsGroup> CREATOR = new Creator<>() {
        @Override
        public ContactsGroup createFromParcel(Parcel in) {
            return new ContactsGroup(in);
        }

        @Override
        public ContactsGroup[] newArray(int size) {
            return new ContactsGroup[size];
        }
    };
}
