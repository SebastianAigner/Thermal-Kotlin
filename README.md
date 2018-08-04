# Thermal-Kotlin
Kotlin Driver for A2 Micro Panel Thermal Printer. Provides an easy to use API to print text and images to the 2.25" printer model. ([Get one here](https://www.adafruit.com/product/597))

![Adafruit Thermal Printer Example](https://i.imgur.com/wsbG4BV.gif)

### Features

- [x] Print text strings
- [x] Underline text
- [ ] Bold Text
- [ ] Inverse Text
- [ ] Automatic Line Breaks for Text
- [ ] Kotlin DSL for composing rich text strings
- [x] Print 1-bit images
- [x] Resize and dither colored images (using [Floyd-Steinberg dithering](https://en.wikipedia.org/wiki/Floyd%E2%80%93Steinberg_dithering))
- [x] Throttled data transfer to allow for large images
- [ ] Print barcodes

### Usage with Gradle

Step 1: Add the JitPack repository to your build file:

```groovy
allprojects {
	repositories {
		//...
		maven { url 'https://jitpack.io' }
	}
}
```

Step 2: Add the dependency:

```groovy
dependencies {
	implementation 'com.github.SebastianAigner:Thermal-Kotlin:master-SNAPSHOT'
}
```

This library is still very new, and has not reached a stable state yet. Expect changes in the API from snapshot to snapshot. To bind yourself to a specific commit, check out the _Commits_ tab on [JitPack](https://jitpack.io/#SebastianAigner/Thermal-Kotlin/).

### How to determine Baud rate

Disconnect the Thermal Printer from the power supply. Press and hold the paper feed button, and plug in the power. The printer will output a Character Code Table as well as its Baudrate and other useful information that might be interesting for debugging.

### Further Reading

- Get a printer for yourself: https://www.adafruit.com/product/597
  - Soldering the printer: https://learn.adafruit.com/pi-thermal-printer/soldering-pre-2017
  - General information on pin setup: https://learn.adafruit.com/mini-thermal-receipt-printer/circuitpython - **Long story short: Ground goes to Ground pin on Raspberry Pi, Yellow goes to Pin 14**.
- https://cdn-shop.adafruit.com/datasheets/cashino+thermal+printer+a2.pdf
- https://cdn-shop.adafruit.com/datasheets/A2-user+manual.pdf