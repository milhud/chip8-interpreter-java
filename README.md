# CHIP-8 Emulator in Java

This is a CHIP-8 Emulator written in Java, developed using the [Cowgod Technical Reference](https://github.com/trapexit/chip-8_documentation/blob/master/Misc/Cowgod's%20CHIP-8%20Technical%20Reference.pdf) for the CHIP-8.

Allows loading and running supported ROM files, and emulates the Chip8 architecture including:

   - 16 8-bit registers
   - 64x32 pixel display
   - Timer-based delay and sound management
   - Keypad mapping (16 keys)
   - Opcode execution

This program uses Pyglet for graphics and audio output. You can install it with the following: 

### To use:

Clone this repository with:

```
git clone https://github.com/milhud/chip8-interpreter-java.git
```

Then, download some CHIP-8 games [from here](https://www.zophar.net/pdroms/chip8/chip-8-games-pack.html) and place them into the project directory (only download games you own, etc., etc.).

Compile with (already compiled for your convenience):

```
javac Chip8Emulator.java
```

Then run with:

```
java Chip8Emulator [PATH_TO_ROM]
```
(adding the path to the ROM)