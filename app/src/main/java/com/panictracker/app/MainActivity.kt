package com.panictracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.panictracker.app.data.entity.SymptomType
import com.panictracker.app.ui.nav.AppNav
import com.panictracker.app.ui.theme.PanicTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleQuickLog(intent)
        setContent {
            PanicTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNav()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleQuickLog(intent)
    }

    /**
     * 런처 아이콘을 길게 눌러 나오는 바로가기로 들어온 경우, 앱을 열자마자 증상을 기록합니다.
     * 공황이 오는 중에는 앱을 열고 탭을 찾는 것조차 부담이라 한 단계라도 줄였습니다.
     */
    private fun handleQuickLog(intent: Intent?) {
        val name = intent?.getStringExtra(EXTRA_QUICK_SYMPTOM) ?: return
        intent.removeExtra(EXTRA_QUICK_SYMPTOM)
        val type = runCatching { SymptomType.valueOf(name) }.getOrNull() ?: return
        lifecycleScope.launch {
            (application as PanicTrackerApp).container.repository.quickLogSymptom(type)
        }
    }

    companion object {
        const val EXTRA_QUICK_SYMPTOM = "quick_symptom"
    }
}
