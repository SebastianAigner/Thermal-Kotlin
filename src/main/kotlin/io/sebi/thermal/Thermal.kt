package io.sebi.thermal

import com.fazecast.jSerialComm.SerialPort
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.net.URL
import java.nio.charset.Charset
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

fun main(args: Array<String>) {
    val tpc = ThermalPrinterController("/dev/serial0", 19200)
    tpc.open()
    tpc.writeString("Hello! Thank you\nFor stopping by!")
    tpc.waitForCompletion()
    tpc.close()
}

class ThermalPrinterController(commPort: String, baudRate: Int) {
    companion object {
        val ESCAPE: Byte = 27
        val FIELD_SEPARATOR: Byte = 28
        val GROUP_SEPARATOR: Byte = 29
        val DEVICE_CONTROL_2: Byte = 18
    }
    val port: SerialPort = SerialPort.getCommPort(commPort)

    init {
        port.baudRate = baudRate
    }

    fun open() {
        port.openPort()
    }

    fun close() {
        while(port.bytesAwaitingWrite() > 0) {
            print("x")
        }
        port.closePort()
    }

    fun writeString(str: String) {
        val terminatedString = if(!str.endsWith("\n")) str + "\n" else str
        val arr = terminatedString.toByteArray(Charset.forName("ASCII"))
        port.writeBytes(arr, arr.size)
        while(port.bytesAwaitingWrite() > 0) {
            //print(".") //ugly busy waiting, but it'll work for now.
        }
    }

    fun underline() {
        //ESCAPE, '-', 2 <- weight
        writeMultiBytes(ESCAPE, '-'.toByte(), 2)
    }

    fun underlineOff() {
        writeMultiBytes(ESCAPE, '-'.toByte(), 0)
    }

    fun writeMultiBytes(vararg bytes: Byte) {
        port.writeBytes(bytes, bytes.size)
        while(port.bytesAwaitingWrite() > 0) {
            //print(".")
        }
    }

    fun writeBufferedImageChunked(img: BufferedImage) {
        //lets figure out how many bytes wide we are.
        val bytesPerLine = Math.ceil(img.width / 8.0).toInt()
        var linesToPrint = img.height
        while(linesToPrint > 0) {
            val lines = mutableListOf<List<Byte>>()
            /**begin chunk**/
            for(currentLine in img.height - linesToPrint until min((img.height - linesToPrint)+100, img.height)) {
                val line = mutableListOf<Byte>()
                for (currentByte in 0 until bytesPerLine) {
                    var byte = 0x0
                    for(bitIndex in 0..7) {
                        val color = img.getPaddedRGB(currentByte * 8 + bitIndex, currentLine)
                        print("$color | ")
                        byte = byte or ((if(color == -16777216) 1 else 0) shl (7-bitIndex)) // we only print perfect black. everything else is white
                    }
                    line.add(byte.toByte())
                }
                println("finished line with ${line.count()} bytes")
                lines.add(line)
                linesToPrint--
            }
            /**end chunk**/
            writeImageCommand(bytesPerLine, lines.count(), lines.flatten().toByteArray())
        }

        println()
        println("image is ${bytesPerLine} wide (for ${img.width}px")
        println("image is ${img.height} px high.")
        //println("data: ${lines.flatten().count()} bytes for ${bytesPerLine*img.height} expected bytes")
        //writeImageCommand(bytesPerLine, img.height, lines.flatten().toByteArray())
        waitForCompletion()
    }

    fun writeColoredBufferedImage(b: BufferedImage) {
        val printableImage =
        if(b.width < 384) {
            b
        }
        else {
            println("Image required scaling.")
            val scaled = b.getScaledInstance(384, -1, Image.SCALE_SMOOTH)
            val bufferedSmall = BufferedImage(scaled.getWidth(null), scaled.getHeight(null), BufferedImage.TYPE_INT_RGB)
            bufferedSmall.graphics.drawImage(scaled, 0,0,null)
            bufferedSmall.graphics.dispose()
            bufferedSmall
        }

        printableImage.grayScale()
        printableImage.dither()
        writeBufferedImageChunked(printableImage)
    }


    fun BufferedImage.getPaddedRGB(x: Int, y: Int): Int {
        return if(0 <= x && x < this.width && 0 <= y && y < this.height) {
            this.getRGB(x,y)
        }
        else {
            -1 //white-pad
        }
    }


    /***
     * Takes the width in BYTE size (pixelwidth/8) ceiled
     */
    fun writeImageCommand(width: Int, height: Int, data: ByteArray) {
        writeMultiBytes(DEVICE_CONTROL_2, '*'.toByte(), height.toByte(), width.toByte())
        println("sending image: ${height.toByte()} height")
        val d = data.toMutableList()
        while(!d.isEmpty()) {
            val currentByte= d.removeAt(0)
            writeMultiBytes(currentByte)
            Thread.sleep(0,250000) //wait 1/4 of a millisecond to send next byte. prevents timing issues
        }
    }

    fun waitForCompletion() {
        while(port.bytesAwaitingWrite() > 0) {
            //print(".")
        }
    }
}

fun SerialPort.writeBytes(bytes: ByteArray, size: Int) {
    this.writeBytes(bytes, size.toLong())
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



fun BufferedImage.inBounds(x: Int, y: Int): Boolean {
    return 0 < x && x < this.width && 0 < y && y < this.height
}

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
