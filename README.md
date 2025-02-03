# CHIP-8 Emulator in Java

This is a CHIP-8 Emulator written in Java, developed using the [Cowgod Technical Reference](https://github.com/trapexit/chip-8_documentation/blob/master/Misc/Cowgod's%20CHIP-8%20Technical%20Reference.pdf) for the CHIP-8.

About the CHIP-8 [from Wikipedia](https://en.wikipedia.org/wiki/CHIP-8):

>CHIP-8 is an interpreted programming language, developed by Joseph Weisbecker on his 1802 microprocessor. It was initially used on the COSMAC VIP and Telmac 1800, which were 8-bit microcomputers made in the mid-1970s.
>CHIP-8 was designed to be easy to program for, as well as using less memory than other programming languages like BASIC.
>Interpreters have been made for many devices, such as home computers, microcomputers, graphing calculators, mobile phones, and video game consoles.

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