package io.sebi.thermal

import java.awt.Color

fun Color.grayscaled(): Color {
    val value = (this.red + this.green + this.blue) / 3
    return Color(value, value, value) //return the color of same value in Color class constructor 
}

fun Color.blackOrWhite(): Color {
    return if ((this.red + this.green + this.blue) / 3 > 128) {
        Color.white  //if white
    } else {
        Color.black //else black
    }
}
