package com.skallahaze.irbloasster

import android.hardware.ConsumerIrManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var ir: ConsumerIrManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(ConsumerIrManager::class.java)

        // Placeholder UI – simple info
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 64, 32, 64)
        }
        val info = android.widget.TextView(this).apply {
            text = if (ir.hasIrEmitter()) {
                "IR‑Blaster gefunden – bereit für Befehle"
            } else {
                "Kein IR‑Blaster verfügbar"
            }
        }
        root.addView(info)
        setContentView(root)

        if (!ir.hasIrEmitter()) {
            Snackbar.make(root, "Dieses Gerät hat keinen IR‑Blaster", Snackbar.LENGTH_LONG).show()
        }
    }
}
