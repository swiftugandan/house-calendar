package dev.housecalendar.app

import android.app.Application

class HouseCalendarApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
