package io.sebi.thermal

import java.awt.Color

fun Color.grayscaled(): Color {
    val value = (this.red + this.green + this.blue) / 3
    return Color(value, value, value)
}

fun Color.blackOrWhite(): Color {
    return if ((this.red + this.green + this.blue) / 3 > 128) {
        Color.white
    } else {
        Color.black
    }
}
