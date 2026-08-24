package com.example.tvbrowser;

import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;

public class TabManager {

    public static class TabItem {
        public String id;
        public String title;
        public String url;
        public WebView webView;

        public TabItem(String id, String title, String url, WebView webView) {
            this.id = id;
            this.title = title != null && !title.isEmpty() ? title : "Nov zavihek";
            this.url = url != null ? url : "";
            this.webView = webView;
        }
    }

    private final List<TabItem> mTabs = new ArrayList<>();
    private int mActiveIndex = 0;

    public void addTab(TabItem tab) {
        mTabs.add(tab);
        mActiveIndex = mTabs.size() - 1;
    }

    public boolean removeTab(int index) {
        if (index < 0 || index >= mTabs.size() || mTabs.size() <= 1) {
            return false;
        }
        TabItem removed = mTabs.remove(index);
        if (removed.webView != null) {
            removed.webView.destroy();
        }
        if (mActiveIndex >= mTabs.size()) {
            mActiveIndex = mTabs.size() - 1;
        }
        return true;
    }

    public TabItem getActiveTab() {
        if (mTabs.isEmpty()) return null;
        if (mActiveIndex < 0 || mActiveIndex >= mTabs.size()) {
            mActiveIndex = 0;
        }
        return mTabs.get(mActiveIndex);
    }

    public int getActiveIndex() {
        return mActiveIndex;
    }

    public void setActiveIndex(int index) {
        if (index >= 0 && index < mTabs.size()) {
            mActiveIndex = index;
        }
    }

    public List<TabItem> getTabs() {
        return mTabs;
    }

    public int getCount() {
        return mTabs.size();
    }
}
