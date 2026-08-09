package com.skallahaze.musiccapsule

/** Exact Float overloads used by the 144 Hz renderer without repeated call-site casts. */
internal fun cos(value: Float): Float = kotlin.math.cos(value.toDouble()).toFloat()

internal fun sin(value: Float): Float = kotlin.math.sin(value.toDouble()).toFloat()
