package android.ext;

import android.content.ComponentName;
import android.content.Intent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/** @hide */
public class ErrorReportUi {

    public static final String ACTION_CUSTOM_REPORT = "action_custom_report";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_GZIPPED_MESSAGE = "gzipped_msg";
    public static final String EXTRA_SOURCE_PACKAGE = "source_pkg";
    public static final String EXTRA_SHOW_REPORT_BUTTON = "show_report_button";

    private static final String HOST_PACKAGE = KnownSystemPackages.SYSTEM_UI;

    public static Intent createBaseIntent(String action, String msg) {
        var i = new Intent(action);
        i.putExtra(ErrorReportUi.EXTRA_GZIPPED_MESSAGE, gzipString(msg));
        // this is needed for correct usage via PendingIntent
        i.setIdentifier(UUID.randomUUID().toString());
        i.setComponent(ComponentName.createRelative(HOST_PACKAGE, ".ErrorReportActivity"));
        return i;
    }

    private static byte[] gzipString(String msg) {
        var bos = new ByteArrayOutputStream(msg.length() / 4);

        try (var s = new GZIPOutputStream(bos)) {
            s.write(msg.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return bos.toByteArray();
    }
}
