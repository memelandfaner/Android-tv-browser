package com.example.tvbrowser;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.webkit.URLUtil;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadHandler {

    public static class DownloadItem {
        public long id;
        public String title;
        public String statusText;
        public String localUri;
        public String mimeType;
        public long bytesSoFar;
        public long totalBytes;

        public DownloadItem(long id, String title, String statusText, String localUri, String mimeType, long bytesSoFar, long totalBytes) {
            this.id = id;
            this.title = title;
            this.statusText = statusText;
            this.localUri = localUri;
            this.mimeType = mimeType;
            this.bytesSoFar = bytesSoFar;
            this.totalBytes = totalBytes;
        }
    }

    public static void enqueueDownload(Context context, String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            if (fileName == null || fileName.isEmpty()) {
                fileName = "download_" + System.currentTimeMillis();
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("Prenašam vsebino prek TV Brskalnika...");
            request.setTitle(fileName);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(context, "⬇️ Začetek prenosa: " + fileName, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "❌ Napaka pri prenosu: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static List<DownloadItem> getDownloadList(Context context) {
        List<DownloadItem> list = new ArrayList<>();
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return list;

        DownloadManager.Query query = new DownloadManager.Query();
        Cursor cursor = null;
        try {
            cursor = dm.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
                int titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int mimeCol = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                int soFarCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);

                do {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    int status = cursor.getInt(statusCol);
                    String localUri = uriCol >= 0 ? cursor.getString(uriCol) : "";
                    String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : "*/*";
                    long soFar = soFarCol >= 0 ? cursor.getLong(soFarCol) : 0;
                    long total = totalCol >= 0 ? cursor.getLong(totalCol) : 0;

                    String statusText = "V teku...";
                    if (status == DownloadManager.STATUS_SUCCESSFUL) statusText = "Končano ✅";
                    else if (status == DownloadManager.STATUS_FAILED) statusText = "Spodletelo ❌";
                    else if (status == DownloadManager.STATUS_PAUSED) statusText = "Zaustavljeno ⏸️";

                    list.add(new DownloadItem(id, title != null ? title : "Datoteka", statusText, localUri, mime, soFar, total));
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public static void openFile(Context context, DownloadItem item) {
        try {
            if (item.localUri == null || item.localUri.isEmpty()) {
                Toast.makeText(context, "Datoteka še ni pripravljena ali prenesena.", Toast.LENGTH_SHORT).show();
                return;
            }

            Uri uri = Uri.parse(item.localUri);
            File file = new File(uri.getPath() != null ? uri.getPath() : item.localUri.replace("file://", ""));

            if (!file.exists()) {
                // Try Download folder path
                File downloadFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), item.title);
                if (downloadFile.exists()) {
                    file = downloadFile;
                }
            }

            Uri contentUri = Uri.fromFile(file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, item.mimeType != null ? item.mimeType : "*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Ni mogoče odpreti datoteke: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
