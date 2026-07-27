package com.countdown.app.widget

import android.content.Intent
import android.widget.RemoteViewsService

class CountdownWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return object : RemoteViewsFactory {
            override fun onCreate() {}
            override fun onDataSetChanged() {}
            override fun onDestroy() {}
            override fun getCount(): Int = 0
            override fun getViewAt(position: Int) = null
            override fun getLoadingView() = null
            override fun getViewTypeCount(): Int = 0
            override fun getItemId(position: Int): Long = 0
            override fun hasStableIds(): Boolean = false
        }
    }
}
