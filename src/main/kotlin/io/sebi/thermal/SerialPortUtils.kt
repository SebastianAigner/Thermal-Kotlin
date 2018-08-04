package io.sebi.thermal

import com.fazecast.jSerialComm.SerialPort

fun SerialPort.writeBytes(bytes: ByteArray, size: Int) {
    this.writeBytes(bytes, size.toLong())
}