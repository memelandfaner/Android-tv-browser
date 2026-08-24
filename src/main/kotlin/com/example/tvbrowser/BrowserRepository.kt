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
        // Safe migration: never drop user bookmarks!
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
    }

    private fun seedDefaults(db: SQLiteDatabase) {
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
                list.add(BookmarkItem(id, title, url, icon))
            }
        }
        return list
    }

    @Synchronized
    fun addBookmark(title: String, url: String, icon: String = "⭐") {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("title", title)
            put("url", url)
            put("icon", icon)
            put("created_at", System.currentTimeMillis())
        }
        db.insert("bookmarks", null, cv)
    }

    @Synchronized
    fun deleteBookmark(id: Int) {
        val db = writableDatabase
        db.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun addHistory(title: String, url: String) {
        if (url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) return
        try {
            val db = writableDatabase
            // Remove previous duplicate for clean chronological history
            db.delete("history", "url = ?", arrayOf(url))
            val cv = ContentValues().apply {
                put("title", title.ifEmpty { url })
                put("url", url)
                put("visited_at", System.currentTimeMillis())
            }
            db.insert("history", null, cv)

            // Keep max 100 entries to prevent DB bloat
            db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visited_at DESC LIMIT 100)")
        } catch (ignored: Exception) {}
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
