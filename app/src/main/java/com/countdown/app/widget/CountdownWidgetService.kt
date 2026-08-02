package com.countdown.app.widget

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * Stub RemoteViewsService — registered in manifest for compatibility.
 * The widget uses a static RemoteViews layout, not a collection,
 * so this service returns no items.
 */
class CountdownWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return object : RemoteViewsFactory {
            override fun onCreate() {}
            override fun onDataSetChanged() {}
            override fun onDestroy() {}
            override fun getCount(): Int = 0
            override fun getViewAt(position: Int) = null
            override fun getLoadingView() = null
            override fun getViewTypeCount(): Int = 1
            override fun getItemId(position: Int): Long = 0L
            override fun hasStableIds(): Boolean = false
        }
    }
}
