package com.jaytext;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.util.Arrays;

@SuppressWarnings("SpellCheckingInspection")
public class Main {

    private static LibC.Termios originalAttributes;
    private static int rows, columns;

    public static void main(String[] args) throws IOException {

        enableRawMode();
        initTextEditor();

        while (true) {
            refreshScreen();
            int key = getKey();
            handleKey(key);
        }
    }

    private static void handleKey(int key) {
        if (key == 'q') {
            LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
            System.exit(0);
        }
        System.out.print((char) key + " (" + key + ")\r\n");
    }

    private static int getKey() throws IOException {
        return System.in.read();
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();

        builder.append("\033[2J");
        builder.append("\033[H");

        for (int i = 0; i < rows - 1; i++) {
            builder.append("~\r\n");
        }

        String statusMessage = "\uD83D\uDCD5 JAYTEXT - v0.0.1 - alpha";
        builder.append("\033[7m")
                .append(statusMessage)
                .append(" ".repeat(Math.max(0, columns - statusMessage.length())))
                .append("\033[0m");

        builder.append("\033[H");
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
