package com.example.tvbrowser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "bookmarks.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE_BOOKMARKS = "bookmarks";

    public static class BookmarkItem {
        public int id;
        public String title;
        public String url;
        public String icon;
        public long createdAt;

        public BookmarkItem(int id, String title, String url, String icon, long createdAt) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.icon = icon;
            this.createdAt = createdAt;
        }
    }

    public BookmarkManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BOOKMARKS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "url TEXT NOT NULL, " +
                "icon TEXT, " +
                "created_at INTEGER);");

        seedDefaults(db);
    }

    private void seedDefaults(SQLiteDatabase db) {
        db.delete(TABLE_BOOKMARKS, null, null);
        // Only the 3 requested default bookmarks
        insertDefault(db, "📺 SmartTube Iskanje", "https://www.youtube.com/results?search_query=", "📺");
        insertDefault(db, "🐙 GitHub", "https://github.com", "🐙");
        insertDefault(db, "🍿 TMDB", "https://www.themoviedb.org", "🍿");
    }

    private void insertDefault(SQLiteDatabase db, String title, String url, String icon) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("url", url);
        cv.put("icon", icon);
        cv.put("created_at", System.currentTimeMillis());
        db.insert(TABLE_BOOKMARKS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        onCreate(db);
    }

    public synchronized void resetToDefaults() {
        SQLiteDatabase db = this.getWritableDatabase();
        seedDefaults(db);
    }

    public synchronized long addBookmark(String title, String url, String icon) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", (title != null && !title.isEmpty()) ? title : url);
        cv.put("url", url);
        cv.put("icon", (icon != null && !icon.isEmpty()) ? icon : "⭐");
        cv.put("created_at", System.currentTimeMillis());
        return db.insert(TABLE_BOOKMARKS, null, cv);
    }

    public synchronized boolean deleteBookmark(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(TABLE_BOOKMARKS, "id = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public synchronized List<BookmarkItem> getAllBookmarks() {
        List<BookmarkItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_BOOKMARKS, null, null, null, null, null, "id ASC");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                    String url = cursor.getString(cursor.getColumnIndexOrThrow("url"));
                    String icon = cursor.getString(cursor.getColumnIndexOrThrow("icon"));
                    long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
                    list.add(new BookmarkItem(id, title, url, icon, createdAt));
                } while (cursor.moveToNext());
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }
}
