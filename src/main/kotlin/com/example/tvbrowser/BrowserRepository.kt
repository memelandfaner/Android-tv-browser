package com.example.tvbrowser

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BrowserRepository(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                icon TEXT,
                created_at INTEGER
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                visited_at INTEGER
            );
        """.trimIndent())

        seedDefaults(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS bookmarks")
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }

    private fun seedDefaults(db: SQLiteDatabase) {
        db.delete("bookmarks", null, null)
        insertBookmarkDirect(db, "📺 YouTube", "https://www.youtube.com", "📺")
        insertBookmarkDirect(db, "🐙 GitHub", "https://github.com", "🐙")
        insertBookmarkDirect(db, "🍿 TMDB", "https://www.themoviedb.org", "🍿")
    }

    private fun insertBookmarkDirect(db: SQLiteDatabase, title: String, url: String, icon: String) {
        val cv = ContentValues().apply {
            put("title", title)
            put("url", url)
            put("icon", icon)
            put("created_at", System.currentTimeMillis())
        }
        db.insert("bookmarks", null, cv)
    }

    @Synchronized
    fun getBookmarks(): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        val db = readableDatabase
        val cursor = db.query("bookmarks", null, null, null, null, null, "id ASC")
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getInt(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val url = it.getString(it.getColumnIndexOrThrow("url"))
                val icon = it.getString(it.getColumnIndexOrThrow("icon")) ?: "⭐"
                val createdAt = it.getLong(it.getColumnIndexOrThrow("created_at"))
                list.add(BookmarkItem(id, title, url, icon, createdAt))
            }
        }
        return list
    }

    @Synchronized
    fun addBookmark(title: String, url: String, icon: String = "⭐"): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", title.ifEmpty { url })
            put("url", url)
            put("icon", icon)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("bookmarks", null, cv)
    }

    @Synchronized
    fun deleteBookmark(id: Int): Boolean {
        val db = writableDatabase
        return db.delete("bookmarks", "id = ?", arrayOf(id.toString())) > 0
    }

    @Synchronized
    fun addHistory(title: String, url: String) {
        if (url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) return
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", title.ifEmpty { url })
            put("url", url)
            put("visited_at", System.currentTimeMillis())
        }
        db.insert("history", null, cv)
    }

    @Synchronized
    fun getHistory(limit: Int = 50): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val db = readableDatabase
        val cursor = db.query("history", null, null, null, null, null, "visited_at DESC", limit.toString())
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getInt(it.getColumnIndexOrThrow("id"))
                val title = it.getString(it.getColumnIndexOrThrow("title"))
                val url = it.getString(it.getColumnIndexOrThrow("url"))
                val visitedAt = it.getLong(it.getColumnIndexOrThrow("visited_at"))
                list.add(HistoryItem(id, title, url, visitedAt))
            }
        }
        return list
    }

    @Synchronized
    fun clearHistory() {
        val db = writableDatabase
        db.delete("history", null, null)
    }

    companion object {
        private const val DATABASE_NAME = "browser_data.db"
        private const val DATABASE_VERSION = 4
    }
}
