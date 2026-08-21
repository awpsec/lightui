package com.lightos.minimalchat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class ApkProvider extends ContentProvider {
    public static final String AUTHORITY = "com.lightos.minimalchat.apkprovider";

    public static Uri uriForFile(Context context, File file) {
        return Uri.parse("content://" + AUTHORITY + "/apk/" + file.getName());
    }

    @Override public boolean onCreate() { return true; }

    @Override public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info.exported) throw new SecurityException("ApkProvider must not be exported");
        if (!info.grantUriPermissions) throw new SecurityException("ApkProvider requires grantUriPermissions");
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = resolve(uri);
        if (file == null || !file.exists()) throw new FileNotFoundException(uri.toString());
        int flags = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode != null && mode.contains("w")) flags = ParcelFileDescriptor.MODE_READ_WRITE;
        return ParcelFileDescriptor.open(file, flags);
    }

    private File resolve(Uri uri) {
        if (uri == null || getContext() == null) return null;
        String path = uri.getPath();
        if (path == null || !path.startsWith("/apk/")) return null;
        String name = path.substring("/apk/".length());
        if (name.contains("/") || name.contains("..")) return null;
        File cache = new File(getContext().getCacheDir(), name);
        if (cache.exists()) return cache;
        File files = new File(getContext().getFilesDir(), name);
        if (files.exists()) return files;
        File extDir = getContext().getExternalFilesDir(null);
        if (extDir != null) {
            File ext = new File(extDir, name);
            if (ext.exists()) return ext;
        }
        return cache;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
