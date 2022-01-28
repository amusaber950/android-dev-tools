package com.trendyol.android.devtools

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.trendyol.android.devtools.analyticslogger.AnalyticsLogger
import com.trendyol.android.devtools.ui.main.MainFragment
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            navigateToFragment(MainFragment.newInstance())
        }

        var inc = 0
        fixedRateTimer("asd", false, 2000, 2000) {
            AnalyticsLogger.report("key: $inc", "value: $inc")
            inc++
        }
    }

    fun navigateToFragment(fragment: Fragment, backStack: String? = null) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, fragment).apply {
                if (backStack.isNullOrBlank().not()) addToBackStack(backStack)
            }
            .commit()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}
