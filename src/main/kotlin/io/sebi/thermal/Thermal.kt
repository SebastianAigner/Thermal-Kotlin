package io.sebi.thermal

import com.fazecast.jSerialComm.SerialPort
import java.awt.Image
import java.awt.image.BufferedImage
import java.net.URL
import java.nio.charset.Charset
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * Demo program to showcase the functionality of the library.
 */
fun main(args: Array<String>) {
    val printer = ThermalPrinter("/dev/serial0", 19200)
    printer.apply {
        connect()
        doubleWidth = true
        justification = ThermalPrinter.Justification.CENTER
        underlined = true
        writeLine("Thermal-Kotlin")
        doubleWidth = false
        writeLine("Printing straight from Kotlin.")
        underlined = false
        justification = ThermalPrinter.Justification.LEFT
        writeText("Kotlin Driver for A2 Micro Panel Thermal Printer. Provides an easy to use API to print text and images to the 2.25\" printer model.")
        writeLine("")
        writeImage(ImageIO.read(URL("https://i.imgur.com/TfanVF2.jpg")))
        writeLine("github.com/Sebastian-Aigner/Thermal-Kotlin")
        disconnect()
    }
}

/**
 * @param commPort the path to the serial communication port, like /dev/serial0
 * @param baudRate the baud rate the printer runs with. Typical value is 19200.
 */
class ThermalPrinter(commPort: String, baudRate: Int) {
    companion object {
        const val ESCAPE: Byte = 27
        const val FIELD_SEPARATOR: Byte = 28
        const val GROUP_SEPARATOR: Byte = 29
        const val DEVICE_CONTROL_2: Byte = 18
    }

    //We use jSerialComm to establish a connection to the physical printer
    private val port: SerialPort = SerialPort.getCommPort(commPort)

    init {
        port.baudRate = baudRate
    }

    /**
     * Establishes a serial connection to the printer
     */
    fun connect() {
        port.openPort()
    }

    /**
     * Waits until all bytes have been sent to the printer and closes the connection.
     */
    fun disconnect() {
        waitForCompletion()
        port.closePort()
    }

    private var _doubleWidth = false
    var doubleWidth get() = _doubleWidth
        set(value) {
            writeMultiBytes(ESCAPE, if(value) 14 else 20)
            _bold = value
        }

    private var _bold = false
    var bold get() = _bold
        set(value) {
            writeMultiBytes(ESCAPE, 'E'.toByte(), if(value) 1 else 0)
            _bold = value
        }

    private var _underlined = false
    var underlined get() = _underlined
        set(value) {
            writeMultiBytes(ESCAPE, '-'.toByte(), if(value) 2 else 0)
            _underlined = value
        }

    private var _inverted = false
    var inverted: Boolean
        get() = _inverted
        set(value) {
            writeMultiBytes(29, 66, if (value) 1 else 0)
            _inverted = value
        }

    enum class Justification {
        LEFT,
        CENTER,
        RIGHT
    }

    private var _justification = Justification.LEFT
    var justification get() = _justification
    set(value) {
        _justification = value
        val indent = when(value) {
            Justification.LEFT -> 0
            Justification.CENTER -> 1
            Justification.RIGHT -> 2
        }
        writeMultiBytes(ESCAPE, 'a'.toByte(), indent.toByte())
    }

    /**
     * Writes a text string to the printer (without applying line-breaking).
     * Automatically terminates the string with a newline if none is present (as the printer will not print a
     * non-terminated line)
     */
    fun writeLine(str: String) {
        val terminatedString = if(!str.endsWith("\n")) str + "\n" else str
        val arr = terminatedString.toByteArray(Charset.forName("ASCII"))
        writeMultiBytes(*arr)
        waitForCompletion()
    }

    /**
     * Print text with line breaks. Uses a simple algorithm that's most likely not perfect, but gets the job done.
     */
    fun writeText(str: String) {
        val lineWidth = 31
        val sb = StringBuilder()
        val words = str.split(" ")
        var remaining = lineWidth
        for(w in words) {
            if(w.length > lineWidth) {
                sb.append("\n")
                sb.append(w)
                sb.append("\n")
                remaining = lineWidth
                continue
            }
            if(w.length <= remaining) {
                sb.append(w)
                sb.append(" ")
            }
            else {
                remaining = lineWidth
                sb.setLength(sb.length - 1) //gets rid of excessive space
                sb.append("\n")
                sb.append(w)
                sb.append(" ")
            }
            remaining -= w.length+1
        }
        writeLine(sb.toString())
    }

    /**
     * Convenience function to simplify crafting multi-byte instructions.
     */
    fun writeMultiBytes(vararg bytes: Byte) {
        port.writeBytes(bytes, bytes.size)
        waitForCompletion()
    }

    /**
     * Prints an already prepared image to the printer.
     * Only accepts true black-and-white imagery, and only prints true black (#000000) to the actual paper.
     * Images higher than 100 pixels are sent in chunks to prevent data being lost due to the limited buffer of the
     * printing device.
     * @param img monochromatic image to be printed
     */
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
                        byte = byte or ((if(color == -16777216) 1 else 0) shl (7-bitIndex)) // we only print perfect black. everything else is white
                    }
                    line.add(byte.toByte())
                }
                lines.add(line)
                linesToPrint--
            }
            /**end chunk**/
            writeImageCommand(bytesPerLine, lines.count(), lines.flatten().toByteArray())
        }

        println()
        println("image is ${bytesPerLine} wide (for ${img.width}px")
        println("image is ${img.height} px high.")
        waitForCompletion()
    }

    /**
     * prints an arbitrary image.
     * If the image is wider than 384px, scale the image down to 384px.
     * The image is automatically converted to grayscale and then dithered using Floyd-Steinberg dithering.
     */
    fun writeImage(b: BufferedImage) {
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
        waitForCompletion()
    }

    /***
     * Takes the width in BYTE size (pixelwidth/8) ceiled
     * @param width amount of bytes required to store one line of the image: ceil(pixel-width /. 8)
     * @param height amount of lines the image is high
     * @param data raw bytes describing the image, where a '1' bit is black, and a '0' bit is white.
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

    /**
     * Wait until all data has been sent at the set Baud rate. This prevents stalling the printer when exiting
     * prematurely or when a lot of data is being sent (e.g. images)
     */
    fun waitForCompletion() {
        while(port.bytesAwaitingWrite() > 0) {
        }
    }
}


