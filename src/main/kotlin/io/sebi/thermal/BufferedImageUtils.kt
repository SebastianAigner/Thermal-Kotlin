package io.sebi.thermal

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min

fun BufferedImage.inBounds(x: Int, y: Int): Boolean {
    return 0 < x && x < this.width && 0 < y && y < this.height
}

fun BufferedImage.getPaddedRGB(x: Int, y: Int): Int {
    return if(0 <= x && x < this.width && 0 <= y && y < this.height) {
        this.getRGB(x,y)
    }
    else {
        -1 //white-pad
    }
}

fun BufferedImage.grayScale() {
    for(y in 0 until this.height) {
        for(x in 0 until this.width) {
            val old = Color(this.getRGB(x,y))
            val new = old.grayscaled()
            this.setRGB(x,y,new.rgb)
        }
    }
}

/* We assume that this only works on grayscaled images*/
fun BufferedImage.dither() {
    for(y in 0 until this.height) {
        for(x in 0 until this.width) {
            val old = Color(this.getRGB(x,y))
            val new = old.blackOrWhite()
            this.setRGB(x,y,new.rgb)
            val quantError = old.red - new.red
            /*
            begin of error propagation
             */
            /*
            x offset
            y offset
            shade offset
             */
            val offsetList = listOf(
                    Triple(x+1, y, quantError * 7 / 16),
                    Triple(x-y, y+1, quantError * 3 / 16),
                    Triple(x, y+1, quantError * 5 / 16),
                    Triple(x+1, y+1, quantError * 1 / 16)
            )

            offsetList.forEach {
                val xCoord = it.first
                val yCoord = it.second
                if(!inBounds(xCoord, yCoord)) return@forEach
                val oldPixel = Color(this.getRGB(xCoord,yCoord))
                val newShade = max(0, min(oldPixel.red + it.third, 255))
                val newPixel = Color(newShade, newShade, newShade)
                this.setRGB(xCoord, yCoord, newPixel.rgb)
            }
        }
    }
}

