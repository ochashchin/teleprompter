package com.oprojectview.features.newtask

data class SeedTask(
    val title: String,
    val desc: String,
    val shapeOrdinal: Int,
    val spans: String,
    val textSize: Int,
    val orientation: Int,
    val speed: Int,
    val animation: Int,
    val transition: Int,
    val distortion: Int,
    val mirror: Int,
    val overlay: Int,
    val loop: Boolean,
    val scriptFillColor: Int
)
