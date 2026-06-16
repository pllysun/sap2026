package edu.csuft.sap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import edu.csuft.sap.notify.ReminderScheduler
import edu.csuft.sap.ui.AppRoot
import edu.csuft.sap.ui.theme.SapTheme
import edu.csuft.sap.widget.ScheduleWidgetProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppRoot()
                }
            }
        }
    }

    // 回桌面时刷新桌面小组件 + 按最新课表重排上课提醒
    override fun onStop() {
        super.onStop()
        ScheduleWidgetProvider.notifyChanged(applicationContext)
        ReminderScheduler.reschedule(applicationContext)
    }
}
