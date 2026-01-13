package com.mc2soft.ontv.update_applications;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CosuUtils {
    public static final String TAG = "CosuSetup";
    public static final boolean DEBUG = false;
    public static final int MSG_DOWNLOAD_COMPLETE = 1;
    public static final int MSG_DOWNLOAD_TIMEOUT = 2;
    public static final int MSG_INSTALL_COMPLETE = 3;
    private static final int DOWNLOAD_TIMEOUT_MILLIS = 120_000;
    public static final String ACTION_INSTALL_COMPLETE
            = "com.mc2soft.ontv.update_applications.INSTALL_COMPLETE";
    private static final String ACTION_UNINSTALL_COMPLETE
            = "com.mc2soft.ontv.update_applications.UNINSTALL_COMPLETE";
    public static Long startDownload(DownloadManager dm, Handler handler, String location) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(location));
        Long id = dm.enqueue(request);
        handler.sendMessageDelayed(handler.obtainMessage(MSG_DOWNLOAD_TIMEOUT, id),
                DOWNLOAD_TIMEOUT_MILLIS);
        if (DEBUG) Log.d(TAG, "Starting download: DownloadId=" + id);
        return id;
    }
    public static boolean installPackage(Context context, InputStream in, String packageName)
            throws IOException {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        // set params
        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);
        OutputStream out = session.openWrite("COSU.apk", 0, -1);
        byte[] buffer = new byte[65536];
        int c;
        while ((c = in.read(buffer)) != -1) {
            out.write(buffer, 0, c);
        }
        session.fsync(out);
        in.close();
        out.close();
        session.commit(createIntentSender(context, sessionId));
        return true;
    }

    private static int getIntentFlag() {
        int flag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            flag |= PendingIntent.FLAG_IMMUTABLE;
        return flag;
    }
    private static IntentSender createIntentSender(Context context, int sessionId) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                new Intent(ACTION_INSTALL_COMPLETE), getIntentFlag());

        return pendingIntent.getIntentSender();
    }
    public static void uninstallPackage(Context context, String packageName) {
        final PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        packageInstaller.uninstall(packageName, createUninstallIntentSender(context, packageName));
    }
    private static IntentSender createInstallIntentSender(Context context, int sessionId) {
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId,
                new Intent(ACTION_INSTALL_COMPLETE), getIntentFlag());
        return pendingIntent.getIntentSender();
    }
    private static IntentSender createUninstallIntentSender(Context context, String packageName) {
        final Intent intent = new Intent(ACTION_UNINSTALL_COMPLETE);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
                intent, getIntentFlag());
        return pendingIntent.getIntentSender();
    }
}