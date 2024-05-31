package com.jaytext;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@SuppressWarnings("SpellCheckingInspection")
public class Main {

    private final static int ARROW_UP = 1000,
            ARROW_DOWN = 1001,
            ARROW_RIGHT = 1002,
            ARROW_LEFT = 1003,
            HOME = 1004,
            END = 1005,
            PAGE_UP = 1006,
            PAGE_DOWN = 1007,
            DEL = 1008;
    private static LibC.Termios originalAttributes;
    private static int rows, columns;
    private static int cursorX = 0, cursorY = 0;
    private static List<String> content = List.of();

    public static void main(String[] args) throws IOException {

        openFile(args);

        enableRawMode();
        initTextEditor();

        while (true) {
            refreshScreen();
            int key = getKey();
            handleKey(key);
        }
    }

    private static void openFile(String[] args) {
        if (args.length == 1) {
            String fileName = args[0];
            Path filePath = Path.of(fileName);
            if (Files.exists(filePath)) {
                try (Stream<String> stringStream = Files.lines(filePath)) {
                    content = stringStream.toList();
                } catch (IOException e) {
                    // TODO add custom message
                }
            }
        }
    }

    private static void handleKey(int key) {
        if (key == 'q') {
            exit();
        } else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, HOME, END).contains(key)){
            moveCursor(key);
//            System.out.print((char) key + " (" + key + ")\r\n");
        }
    }

    private static void exit() {
        System.out.println("\033[2J");
        System.out.println("\033[H");
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
        System.exit(0);
    }

    private static void moveCursor(int key) {
        switch (key) {
            case ARROW_UP -> {
                if (cursorY > 0) {
                    cursorY--;
                }
            }
            case ARROW_DOWN -> {
                if (cursorY < rows - 1) {
                    cursorY++;
                }
            }
            case ARROW_LEFT -> {
                if (cursorX > 0) {
                    cursorX--;
                }
            }
            case ARROW_RIGHT -> {
                if (cursorX < columns - 1) {
                    cursorX++;
                }
            }
            case HOME -> cursorX = 0;
            case END -> cursorX = columns - 1;
        }
    }

    private static int getKey() throws IOException {
        int key = System.in.read();
        if (key != '\033') {
            return key;
        }

        int nextKey = System.in.read();
        if (nextKey != '[' && nextKey != 'O') {
            return nextKey;
        }

        int anotherKey = System.in.read();
        if (nextKey == '[') {
            return switch (anotherKey) {
                case 'A' -> ARROW_UP;
                case 'B' -> ARROW_DOWN;
                case 'C' -> ARROW_RIGHT;
                case 'D' -> ARROW_LEFT;
                case 'H' -> HOME;
                case 'F' -> END;
                case '0','1','2','3','4','5','6','7','8','9' -> {
                    int secondAnotherKey = System.in.read();
                    if (secondAnotherKey != '~') {
                        yield secondAnotherKey;
                    }
                    switch (anotherKey) {
                        case '1':
                        case '7':
                            yield HOME;
                        case '3':
                            yield DEL;
                        case '4':
                        case '8':
                            yield END;
                        case '5':
                            yield PAGE_UP;
                        case '6':
                            yield PAGE_DOWN;
                        default:
                            yield anotherKey;
                    }
                }
                default -> anotherKey;
            };
        } else {
            return switch (anotherKey) {
                case 'H' -> HOME;
                case 'F' -> END;
                default -> anotherKey;
            };
        }
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();

        // Clears the whole screen
//        builder.append("\033[2J");
        // Moves the caret to top-left corner
        builder.append("\033[H");

        for (int i = 0; i < rows - 1; i++) {
            if (i >= content.size()) {
                builder.append("~");
            } else {
                builder.append(content.get(i));
            }
            // Erase in the line content that were previously written
            builder.append("\033[K\r\n");
        }

        String statusMessage = "\uD83D\uDCD5 JAYTEXT - v0.0.1 - alpha";
        builder.append("\033[7m")
                .append(statusMessage)
                .append(" ".repeat(Math.max(0, columns - statusMessage.length())))
                .append("\033[0m");

        // Update the caret position after pressing an arrow key
        builder.append(String.format("\033[%d;%dH", cursorY + 1, cursorX + 1));
        System.out.print(builder);
    }

    private static void initTextEditor() {
        LibC.Winsize winsize = getWindowSize();
        rows = winsize.ws_row;
        columns = winsize.ws_col;
    }

    private static void enableRawMode() {
        LibC.Termios termios = new LibC.Termios();
        int rc = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);

        if (rc != 0) {
            System.out.println("there was problem with tcgetattr");
            System.exit(rc);
        }

        originalAttributes = LibC.Termios.of(termios);

        termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
        termios.c_iflag &= ~(LibC.IXON | LibC.ICRNL);
        termios.c_oflag &= ~(LibC.OPOST);

        /*termios.c_cc[LibC.VMIN] = 0;
        termios.c_cc[LibC.VTIME] = 1;*/

        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios);
    }

    private static LibC.Winsize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();
        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winsize);

        if (rc != 0) {
            System.err.println("ioctl failed with return code[={}]" + rc);
            System.exit(1);
        }
        return winsize;
    }
}

@SuppressWarnings("SpellCheckingInspection")
interface LibC extends Library {

    int SYSTEM_OUT_FD = 0;
    int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
            IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1,
            VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x5413;

    // loading the C standard library for POSIX systems
    LibC INSTANCE = Native.load("c", LibC.class);

    int tcgetattr(int fd, Termios termios);

    int tcsetattr(int fd, int optional_actions, Termios termios);

    int ioctl(int fd, int opt, Winsize winsize);

    @Structure.FieldOrder(value = {"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
    class Winsize extends Structure {
        public short ws_row, ws_col, ws_xpixel, ws_ypixel;
    }

    @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})
    class Termios extends Structure {
        public int c_iflag;      /* input modes */
        public int c_oflag;      /* output modes */
        public int c_cflag;      /* control modes */
        public int c_lflag;      /* local modes */
        public byte[] c_cc = new byte[19];   /* special characters */

        public Termios() {
        }

        public static Termios of(Termios t) {
            Termios copy = new Termios();
            copy.c_iflag = t.c_iflag;
            copy.c_oflag = t.c_oflag;
            copy.c_cflag = t.c_cflag;
            copy.c_lflag = t.c_lflag;
            copy.c_cc = t.c_cc;
            return copy;
        }

        @Override
        public String toString() {
            return "Termios{" +
                    "c_iflag=" + c_iflag +
                    ", c_oflag=" + c_oflag +
                    ", c_cflag=" + c_cflag +
                    ", c_lflag=" + c_lflag +
                    ", c_cc=" + Arrays.toString(c_cc) +
                    '}';
        }
    }
}
