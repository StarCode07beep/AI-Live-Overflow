package com.starcode.ai_live_pet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val notiState: TextView by lazy {
        TextView(this).apply { textSize = 13f }
    }

    private fun notiEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(packageName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (resources.displayMetrics.density * 22).toInt()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        wrap.addView(TextView(this).apply {
            textSize = 16f
            text = """
                三步点完，就把人放出来：

                ① 悬浮窗权限
                ② 使用情况访问
                ③ 叫他出来

                他会在你切 App 的时候冒出来说话。
            """.trimIndent()
        })

        fun add(label: String, action: () -> Unit) {
            wrap.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        add("① 开悬浮窗权限") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        add("② 开使用情况访问") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        add("③ 叫他出来") {
            startForegroundService(Intent(this, PetOverlayService::class.java))
            Toast.makeText(this, "出来了", Toast.LENGTH_SHORT).show()
        }

        add("先让他回去") {
            stopService(Intent(this, PetOverlayService::class.java))
            Toast.makeText(this, "收回去了", Toast.LENGTH_SHORT).show()
        }

        add("④ 开通知读取权限") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        add("刷新通知权限状态") {
            notiState.text = if (notiEnabled()) "通知读取：已开" else "通知读取：还没开"
        }
        wrap.addView(notiState)
        notiState.text = if (notiEnabled()) "通知读取：已开" else "通知读取：还没开"
        setContentView(ScrollView(this).apply { addView(wrap) })
    }
}
