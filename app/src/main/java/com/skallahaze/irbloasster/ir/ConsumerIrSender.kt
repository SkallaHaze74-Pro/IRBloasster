package com.skallahaze.irbloasster.ir

import android.content.Context
import android.hardware.ConsumerIrManager

object ConsumerIrSender {
    private lateinit var ir: ConsumerIrManager
    fun init(ctx: Context) {
        ir = ctx.getSystemService(Context.CONSUMER_IR_SERVICE) as ConsumerIrManager
        check(ir.hasIrEmitter()) { "Kein IR-Blaster vorhanden" }
    }
    fun transmit(freq: Int, pattern: IntArray) {
        ir.transmit(freq, pattern)
    }
}
