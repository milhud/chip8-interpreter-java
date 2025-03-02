import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.Stack;
import javax.sound.sampled.*; // for sound
import javax.swing.*;         // for graphics

public class Chip8Emulator extends JPanel implements KeyListener
{
  // CHIP‑8 components:
  private Stack<Integer> stack = new Stack<> ();
  private int[] V = new int[16]; // General purpose registers V0 to VF
  private int[] display
      = new int[64 * 32]; // 64x32 pixel display - 1 for on 1 for off
  private int[] keyInputs = new int[16]; // CHIP‑8 keypad (16 keys)
  private int[] memory = new int[4096];  // 4K memory

  // fontset: each character is 5 bytes - will be loaded on initialization
  private int[] fonts = {
    0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
    0x20, 0x60, 0x20, 0x20, 0x70, // 1
    0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
    0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
    0x90, 0x90, 0xF0, 0x10, 0x10, // 4
    0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
    0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
    0xF0, 0x10, 0x20, 0x40, 0x40, // 7
    0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
    0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
    0xF0, 0x90, 0xF0, 0x90, 0x90, // A
    0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
    0xF0, 0x80, 0x80, 0x80, 0xF0, // C
    0xE0, 0x90, 0x90, 0x90, 0xE0, // D
    0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
    0xF0, 0x80, 0xF0, 0x80, 0x80  // F
  };

  // timers
  private int delayTimer = 0;
  private int soundTimer = 0;

  // Other registers
  private int opcode = 0;
  private int I = 0;  // Index register
  private int pc = 0; // Program counter

  // for opcode decoding
  private int vx;
  private int vy;

  // Whether the display should be redrawn:
  private boolean shouldDraw = false;

  //  beep sound
  private Clip beep;

  // main emulation loop timer
  private Timer timer;

  // Constructor – set up the window and key listener.
  public Chip8Emulator ()
  {
    JFrame frame = new JFrame ("CHIP8 Emulator");
    frame.setDefaultCloseOperation (JFrame.EXIT_ON_CLOSE);
    frame.setSize (640, 320); // 10x scale of 64x32
    frame.add (this);
    frame.setResizable (false);
    frame.setVisible (true);

    // key listener
    setFocusable (true);
    addKeyListener (this);

    // Attempt to load a beep sound
    try
      {
        AudioInputStream audioIn
            = AudioSystem.getAudioInputStream (new File ("beep.wav"));
        beep = AudioSystem.getClip ();
        beep.open (audioIn);
      }
    catch (Exception e)
      {
        System.err.println ("Beep sound not loaded.");
      }
  }

  // initialize the emulator
  public void
  initialize ()
  {
    Arrays.fill (memory, 0);
    Arrays.fill (V, 0);
    Arrays.fill (display, 0);
    Arrays.fill (keyInputs, 0);
    stack.clear ();
    delayTimer = 0;
    soundTimer = 0;
    pc = 0x200; // programs start at 0x200.
    I = 0;
    shouldDraw = false;

    // Load fonts into memory (starting at address 0)
    for (int i = 0; i < fonts.length; i++)
      {
        memory[i] = fonts[i];
      }
  }

  // load ROM bytes into memory starting at address 0x200
  public void
  loadRom (String path)
  {
    try
      {
        byte[] data = Files.readAllBytes (Paths.get (path));
        for (int i = 0; i < data.length; i++)
          {
            memory[0x200 + i] = data[i] & 0xFF; // data is already in byte form
                                                // but just extra checking
          }
      }
    catch (IOException e)
      {
        e.printStackTrace (); // we need to handle this excepiton
      }
  }

  // fetch, decode, execute, and update timers.
  public void
  cycle ()
  {
    // Fetch 2-byte opcode
    opcode = (memory[pc] << 8)
             | memory[pc + 1]; // bytes at the next two program counter
                               // locations, combining into 16 bits
    pc += 2;

    // decode registers from opcode
    vx = (opcode & 0x0F00) >> 8;
    vy = (opcode & 0x00F0) >> 4;

    // Decode and execute opcode based on most significant nibble
    int op = opcode & 0xF000; // for more grouping, we will group based on most
                              // significant nibble
    switch (op)
      {
      case 0x0000:
        if (opcode == 0x00E0)
          { // 00E0: Clear display
            op_00E0 ();
          }
        else if (opcode == 0x00EE)
          { // 00EE: Return from subroutine
            op_00EE ();
          }
        break;
      case 0x1000:
        op_1NNN (); // jump to address NNN
        break;
      case 0x2000:
        op_2NNN ();
        break; // Call subroutine at NNN
      case 0x3000:
        op_3XNN (); // skip next instruction if Vx == NN
        break;
      case 0x4000:
        op_4XNN (); // Skip next instruction if Vx != NN
        break;
      case 0x5000:
        op_5XY0 (); // skip next instruction if Vx == Vy
        break;
      case 0x6000:
        op_6XNN (); // Set Vx = NN
        break;
      case 0x7000:
        op_7XNN (); // set Vx = Vx + NN
        break;
      case 0x8000:
        switch (opcode & 0x000F)
          { // now least significant nibble
          case 0x0:
            op_8XY0 (); // Set Vx = Vy
            break;
          case 0x1:
            op_8XY1 (); // Set Vx = Vx OR Vy
            break;
          case 0x2:
            op_8XY2 (); // set Vx = Vx AND Vy
            break;
          case 0x3:
            op_8XY3 (); // Set Vx = Vx XOR Vy
            break;
          case 0x4:
            op_8XY4 (); // set Vx = Vx + Vy, set VF = carry
            break;
          case 0x5:
            op_8XY5 (); // Set Vx = Vx - Vy, set VF = NOT borrow
            break;
          case 0x6:
            op_8XY6 (); // set Vx = Vx SHR 1
            break;
          case 0x7:
            op_8XY7 (); // set Vx = Vy - Vx, set VF = NOT borrow
            break;
          case 0xE:
            op_8XYE (); // Set Vx = Vx SHL 1
            break;
          default:
            System.out.printf ("Unknown opcode: %X\n", opcode);
            break;
          }
        break;
      case 0x9000:
        op_9XY0 (); // skip next instruction if Vx != Vy
        break;
      case 0xA000:
        op_ANNN (); // set I = NNN
        break;
      case 0xB000:
        op_BNNN (); // Jump to address NNN + V0
        break;
      case 0xC000:
        op_CXNN (); // set Vx = random byte AND NN
        break;
      case 0xD000:
        op_DXYN (); // draw sprite at (Vx, Vy) with width 8 and height N
        break;
      case 0xE000:
        if ((opcode & 0x00FF) == 0x9E)
          { // Skip next instruction if key with the value of Vx is pressed
            op_EX9E ();
          }
        else if ((opcode & 0x00FF) == 0xA1)
          { // EXA1: Skip next instruction if key with the value of Vx is not
            // pressed
            op_EXA1 ();
          }
        break;
      case 0xF000:
        switch (opcode & 0x00FF)
          {
          case 0x07:
            op_FX07 (); // set Vx = delay timer value
            break;
          case 0x0A:
            op_FX0A (); // wait for a key press, store the value in Vx
            break;
          case 0x15:
            op_FX15 (); // Set delay timer = Vx
            break;
          case 0x18:
            op_FX18 (); // set sound timer = Vx
            break;
          case 0x1E:
            op_FX1E (); // Set I = I + Vx
            break;
          case 0x29:
            op_FX29 (); // set I = location of sprite for digit Vx
            break;
          case 0x33:
            op_FX33 (); // Store BCD representation of Vx in memory locations I,
                        // I+1, and I+2
            break;
          case 0x55:
            op_FX55 (); // store registers V0 through Vx in memory starting at I
            break;
          case 0x65:
            op_FX65 (); // read registers V0 through Vx from memory starting at
                        // I
            break;
          default:
            System.out.printf ("Unknown opcode: %X\n", opcode);
            break;
          }
        break;
      default:
        System.out.printf ("Unknown opcode: %X\n", opcode);
        break;
      }

    // Update timers
    if (delayTimer > 0)
      {
        delayTimer--;
      }

    if (soundTimer > 0)
      {
        soundTimer--;
        if (soundTimer == 0 && beep != null)
          {
            beep.setFramePosition (0); // play clip from beginning
            beep.start ();
          }
      }
  }

  // Opcode implementations

  // 00E0 - clear the display
  private void
  op_00E0 ()
  {
    Arrays.fill (display, 0);
    shouldDraw = true;
  }

  // 00EE - return from subroutine
  private void
  op_00EE ()
  {
    pc = stack.pop ();
  }

  // 1NNN - jump to address NNN
  private void
  op_1NNN ()
  {
    int addr = opcode & 0x0FFF;
    pc = addr;
  }

  // 2NNN -  call subroutine at NNN
  private void
  op_2NNN ()
  {
    stack.push (pc);
    pc = opcode & 0x0FFF;
  }

  // 3XNN - skip next instruction if Vx equals NN
  private void
  op_3XNN ()
  {
    int nn = opcode & 0x00FF;
    if (V[vx] == nn)
      {
        pc += 2;
      }
  }

  // 4XNN - skip next instruction if Vx doesn't equal NN
  private void
  op_4XNN ()
  {
    int nn = opcode & 0x00FF;
    if (V[vx] != nn)
      {
        pc += 2;
      }
  }

  // 5XY0 - skip next instruction if Vx equals Vy
  private void
  op_5XY0 ()
  {
    if (V[vx] == V[vy])
      {
        pc += 2;
      }
  }

  // 6XNN - set Vx = NN
  private void
  op_6XNN ()
  {
    V[vx] = opcode & 0x00FF;
  }

  // 7XNN - set Vx = Vx + NN
  private void
  op_7XNN ()
  {
    V[vx] = (V[vx] + (opcode & 0x00FF)) & 0xFF;
  }

  // 8XY0 - set Vx = Vy
  private void
  op_8XY0 ()
  {
    V[vx] = V[vy];
  }

  // 8XY1 - set Vx = Vx OR Vy
  private void
  op_8XY1 ()
  {
    V[vx] |= V[vy];
    V[vx] &= 0xFF;
  }

  // 8XY2 - set Vx = Vx AND Vy
  private void
  op_8XY2 ()
  {
    V[vx] &= V[vy];
    V[vx] &= 0xFF;
  }

  // 8XY3 - set Vx = Vx XOR Vy
  private void
  op_8XY3 ()
  {
    V[vx] ^= V[vy];
    V[vx] &= 0xFF;
  }

  // 8XY4 - set Vx = Vx + Vy, set VF = carry
  private void
  op_8XY4 ()
  {
    int sum = V[vx] + V[vy];
    V[0xF] = (sum > 0xFF) ? 1 : 0;
    V[vx] = sum & 0xFF;
  }

  // 8XY5 - set Vx = Vx - Vy, set VF = NOT borrow
  private void
  op_8XY5 ()
  {
    V[0xF] = (V[vx] >= V[vy]) ? 1 : 0;
    V[vx] = (V[vx] - V[vy]) & 0xFF;
  }

  // 8XY6 - set Vx = Vx SHR 1
  private void
  op_8XY6 ()
  {
    V[0xF] = V[vx] & 0x1;
    V[vx] = (V[vx] >> 1) & 0xFF;
  }

  // 8XY7: - set Vx = Vy - Vx, set VF = NOT borrow
  private void
  op_8XY7 ()
  {
    V[0xF] = (V[vy] >= V[vx]) ? 1 : 0;
    V[vx] = (V[vy] - V[vx]) & 0xFF;
  }

  // 8XYE - set Vx = Vx SHL 1
  private void
  op_8XYE ()
  {
    V[0xF] = (V[vx] & 0x80) >> 7;
    V[vx] = (V[vx] << 1) & 0xFF;
  }

  // 9XY0 - skip next instruction if Vx doesn't equal Vy
  private void
  op_9XY0 ()
  {
    if (V[vx] != V[vy])
      {
        pc += 2;
      }
  }

  // ANNN - set I = NNN
  private void
  op_ANNN ()
  {
    I = opcode & 0x0FFF;
  }

  // BNNN - jump to address NNN + V0
  private void
  op_BNNN ()
  {
    pc = (opcode & 0x0FFF) + V[0];
  }

  // CXNN - set Vx = random byte AND NN
  private void
  op_CXNN ()
  {
    int randByte = new Random ().nextInt (256);
    V[vx] = randByte & (opcode & 0x00FF);
  }

  // DXYN - draw sprite at (Vx, Vy) with width 8 and height N
  private void
  op_DXYN ()
  {
    int x = V[vx] & 0xFF;
    int y = V[vy] & 0xFF;
    int height = opcode & 0x000F;
    V[0xF] = 0;
    for (int row = 0; row < height; row++)
      {
        int spriteByte = memory[I + row];
        for (int col = 0; col < 8; col++)
          {
            int pixel = (spriteByte >> (7 - col)) & 1;
            int posX = (x + col) % 64;
            int posY = (y + row) % 32;
            int index = posY * 64 + posX;
            if (pixel == 1)
              {
                if (display[index] == 1)
                  {
                    V[0xF] = 1;
                  }
                display[index] ^= 1;
              }
          }
      }
    shouldDraw = true;
  }

  // EX9E - skip next instruction if key with the value of Vx is pressed
  private void
  op_EX9E ()
  {
    if (keyInputs[V[vx]] == 1)
      {
        pc += 2;
      }
  }

  // EXA1 - skip next instruction if key with the value of Vx is not pressed
  private void
  op_EXA1 ()
  {
    if (keyInputs[V[vx]] == 0)
      {
        pc += 2;
      }
  }

  // FX07 = set Vx = delay timer.
  private void
  op_FX07 ()
  {
    V[vx] = delayTimer;
  }

  // FX0A - wait for a key press, store the key in Vx
  private void
  op_FX0A ()
  {
    boolean keyPressed = false;
    for (int i = 0; i < keyInputs.length; i++)
      {
        if (keyInputs[i] == 1)
          {
            V[vx] = i;
            keyPressed = true;
            break;
          }
      }
    if (!keyPressed)
      {
        pc -= 2; // repeat this opcode until a key is pressed
      }
  }

  // FX15 - set delay timer = Vx
  private void
  op_FX15 ()
  {
    delayTimer = V[vx];
  }

  // FX18 - set sound timer = Vx
  private void
  op_FX18 ()
  {
    soundTimer = V[vx];
  }

  // FX1E - set I = I + Vx
  private void
  op_FX1E ()
  {
    I = (I + V[vx]) & 0xFFF;
  }

  // FX29 - set I = location of sprite for digit Vx
  private void
  op_FX29 ()
  {
    I = (V[vx] & 0xFF) * 5;
  }

  // FX33 - store BCD representation of Vx in memory locations I, I+1, and I+2
  private void
  op_FX33 ()
  {
    memory[I] = V[vx] / 100;
    memory[I + 1] = (V[vx] % 100) / 10;
    memory[I + 2] = V[vx] % 10;
  }

  // FX55 - store registers V0 through Vx in memory starting at I
  private void
  op_FX55 ()
  {
    for (int i = 0; i <= vx; i++)
      {
        memory[I + i] = V[i];
      }
    I += vx + 1;
  }

  // FX65 - rad registers V0 through Vx from memory starting at I
  private void
  op_FX65 ()
  {
    for (int i = 0; i <= vx; i++)
      {
        V[i] = memory[I + i];
      }
    I += vx + 1;
  }

  // paint display, with each CHIP‑8 pixel as a 10×10 rectangle
  @Override
  public void
  paintComponent (Graphics g)
  {
    super.paintComponent (g);
    for (int y = 0; y < 32; y++)
      {
        for (int x = 0; x < 64; x++)
          {                                  // loop through 64 * 32 display
            int pixel = display[y * 64 + x]; // get pixel value at display
            g.setColor (pixel == 1 ? Color.WHITE : Color.BLACK); // black if 0
            g.fillRect (x * 10, y * 10, 10,
                        10); // draw pixel at calculated position
          }
      }
  }

  // keylistener
  @Override
  public void
  keyPressed (KeyEvent e)
  { // key event - for when key is pressed
    int key = e.getKeyCode ();
    // maps keys to chip 8 keypad
    switch (key)
      {
      case KeyEvent.VK_1:
        keyInputs[0x1] = 1;
        break;
      case KeyEvent.VK_2:
        keyInputs[0x2] = 1;
        break;
      case KeyEvent.VK_3:
        keyInputs[0x3] = 1;
        break;
      case KeyEvent.VK_4:
        keyInputs[0xC] = 1;
        break;
      case KeyEvent.VK_Q:
        keyInputs[0x4] = 1;
        break;
      case KeyEvent.VK_W:
        keyInputs[0x5] = 1;
        break;
      case KeyEvent.VK_E:
        keyInputs[0x6] = 1;
        break;
      case KeyEvent.VK_R:
        keyInputs[0xD] = 1;
        break;
      case KeyEvent.VK_A:
        keyInputs[0x7] = 1;
        break;
      case KeyEvent.VK_S:
        keyInputs[0x8] = 1;
        break;
      case KeyEvent.VK_D:
        keyInputs[0x9] = 1;
        break;
      case KeyEvent.VK_F:
        keyInputs[0xE] = 1;
        break;
      case KeyEvent.VK_Z:
        keyInputs[0xA] = 1;
        break;
      case KeyEvent.VK_X:
        keyInputs[0x0] = 1;
        break;
      case KeyEvent.VK_C:
        keyInputs[0xB] = 1;
        break;
      case KeyEvent.VK_V:
        keyInputs[0xF] = 1;
        break;
      }
  }

  // for when keys are released
  @Override
  public void
  keyReleased (KeyEvent e)
  {
    int key = e.getKeyCode ();
    switch (key)
      {
      case KeyEvent.VK_1:
        keyInputs[0x1] = 0;
        break;
      case KeyEvent.VK_2:
        keyInputs[0x2] = 0;
        break;
      case KeyEvent.VK_3:
        keyInputs[0x3] = 0;
        break;
      case KeyEvent.VK_4:
        keyInputs[0xC] = 0;
        break;
      case KeyEvent.VK_Q:
        keyInputs[0x4] = 0;
        break;
      case KeyEvent.VK_W:
        keyInputs[0x5] = 0;
        break;
      case KeyEvent.VK_E:
        keyInputs[0x6] = 0;
        break;
      case KeyEvent.VK_R:
        keyInputs[0xD] = 0;
        break;
      case KeyEvent.VK_A:
        keyInputs[0x7] = 0;
        break;
      case KeyEvent.VK_S:
        keyInputs[0x8] = 0;
        break;
      case KeyEvent.VK_D:
        keyInputs[0x9] = 0;
        break;
      case KeyEvent.VK_F:
        keyInputs[0xE] = 0;
        break;
      case KeyEvent.VK_Z:
        keyInputs[0xA] = 0;
        break;
      case KeyEvent.VK_X:
        keyInputs[0x0] = 0;
        break;
      case KeyEvent.VK_C:
        keyInputs[0xB] = 0;
        break;
      case KeyEvent.VK_V:
        keyInputs[0xF] = 0;
        break;
      }
  }

  @Override
  public void
  keyTyped (KeyEvent e)
  {
    // don't know what to do with this - must implement method
  }

  // main loop - swing timer to run cycles
  public void
  runEmulator ()
  {
    timer = new Timer (
        2,
        new ActionListener () { // will perform an action every 2 milliseconds
          @Override public void actionPerformed (ActionEvent e)
          {
            cycle ();
            if (shouldDraw)
              {
                repaint ();
                shouldDraw = false;
              }
          }
        });

    timer.start (); // starts timer
  }

  // main function
  public static void
  main (String[] args)
  {
    Chip8Emulator emulator = new Chip8Emulator ();
    emulator.initialize ();

    // begins loading rom
    if (args.length > 0)
      {
        emulator.loadRom (args[0]);
      }
    else
      {
        System.out.println ("To use - java Chip8Emulator <path_to_rom>");
        System.exit (0);
      }

    // run emulator
    emulator.runEmulator ();
  }
}