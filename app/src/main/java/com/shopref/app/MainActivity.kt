package com.shopref.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shopref.app.state.KeyValueStore
import com.shopref.app.ui.ShopRefApp
import com.shopref.app.ui.theme.ShopRefTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SharedPrefsStore(getSharedPreferences("shopref", Context.MODE_PRIVATE))
        setContent {
            ShopRefTheme {
                ShopRefApp(store, java.util.Locale.getDefault().language)
            }
        }
    }
}

private class SharedPrefsStore(private val prefs: android.content.SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
