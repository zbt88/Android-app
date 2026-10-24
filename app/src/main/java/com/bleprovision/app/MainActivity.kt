package com.bleprovision.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val status = findViewById<TextView>(R.id.statusText)
        findViewById<Button>(R.id.scanButton).setOnClickListener {
            status.text = "蓝牙扫描功能将在下一阶段实现"
        }
    }
}
