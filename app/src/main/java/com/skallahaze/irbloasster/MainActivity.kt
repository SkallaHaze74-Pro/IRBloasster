package com.skallahaze.irbloasster

import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private lateinit var ir: ConsumerIrManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(ConsumerIrManager::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 64)
        }

        fun addBtn(label: String, codeHex: String) {
            val btn = Button(this).apply { text = label }
            btn.setOnClickListener {
                if (ir.hasIrEmitter()) {
                    ir.transmit(38_000, Nec.hexToPattern(codeHex))
                } else {
                    Snackbar.make(root, "Kein IR‑Blaster vorhanden", Snackbar.LENGTH_SHORT).show()
                }
            }
            root.addView(btn)
        }

        // LG OLED55B1 – Standard‑Codes
        addBtn("Power", "20DF10EF")
        addBtn("Volume +", "20DF40BF")
        addBtn("Volume –", "20DFC03F")
        addBtn("Source", "20DFD02F")

        setContentView(root)
    }
}
