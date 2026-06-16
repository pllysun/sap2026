package edu.csuft.sap

import android.app.Application
import edu.csuft.sap.di.Graph

class SapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
