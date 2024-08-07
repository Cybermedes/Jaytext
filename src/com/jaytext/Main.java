package com.jaytext;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
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
    private static final int BACKSPACE = 127;
    private static LibC.Termios originalAttributes;
    private static int rows, columns;
    private static int cursorX = 0, cursorY = 0, offsetY = 0, offsetX = 0;
    private static List<String> content = new ArrayList<>();
    private static Path currentFile;

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

    private static void scrollPage() {
        if (cursorY >= rows + offsetY) {
            offsetY = cursorY - rows + 1;
        } else if (cursorY < offsetY) {
            offsetY = cursorY;
        }

        if (cursorX >= columns + offsetX) {
            offsetX = cursorX - columns + 1;
        } else if (cursorX < offsetX) {
            offsetX = cursorX;
        }
    }

    private static void openFile(String[] args) {
        if (args.length == 1) {
            String fileName = args[0];
            Path filePath = Path.of(fileName);
            if (Files.exists(filePath)) {
                try (Stream<String> stringStream = Files.lines(filePath)) {
                    content = stringStream.collect(Collectors.toCollection(ArrayList::new));
                } catch (IOException e) {
                    // TODO add custom message
                }
            }
            currentFile = filePath;
        }
    }

    private static void handleKey(int key) {
        if (key == control('q')) {
            exit();
        } else if (key == '\r') {
            handleEnter();
        } else if (key == control('s')) {
            editorSave();
        } else if (key == control('f')) {
            editorFind();
        } else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, HOME, END, PAGE_UP, PAGE_DOWN).contains(key)) {
            moveCursor(key);
        } else if(List.of(DEL, BACKSPACE, control('h')).contains(key)) {
            deleteChar();
        } else {
            insertChar((char) key);
        }
    }

    private static void editorSave() {
        try {
            Files.write(currentFile, content);
            setStatusMessage("File has been saved");
        } catch (IOException e) {
            setStatusMessage("File not saved: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void deleteChar() {
        if (cursorX == 0 && cursorY == 0) return;
        if (cursorY == content.size()) return;
        if (cursorX > 0) {
            deleteCharFromRow(cursorY, cursorX - 1);
            cursorX--;
        } else {
            cursorX = content.get(cursorY - 1).length();
            appendStringToRow(cursorY -1,content.get(cursorY));
            deleteRow(cursorY);
            cursorY--;
        }
    }

    private static void deleteRow(int at) {
        if (at < 0 || at >= content.size()) return;
        content.remove(at);
    }

    private static void appendStringToRow(int at, String append) {
        content.set(at, content.get(at) + append);
    }

    private static void deleteCharFromRow(int row, int at) {
        String line = content.get(row);
        if (at < 0 || at > line.length()) return;
        String editedLine = new StringBuilder(line).deleteCharAt(at).toString();
        content.set(row, editedLine);

    }

    private static void handleEnter() {
        if (cursorX == 0) {
            insertRowAt(cursorY, "");
        } else {
            String line = content.get(cursorY);
            insertRowAt(cursorY + 1, line.substring(cursorX));
            content.set(cursorY, line.substring(0, cursorX));
        }
        cursorY++;
        cursorX = 0;
    }

    private static void insertChar(char key) {

        if (cursorY == content.size()) {
            insertRowAt(cursorY, "");
        }

        insertCharInRow(cursorY, cursorX, key);
        cursorX++;
    }

    private static void insertRowAt(int at, String rowContent) {
        if (at < 0 || at > content.size()) return;
        content.add(at, rowContent);
    }

    private static void insertCharInRow(int row, int at, char key) {
        String line = content.get(row);
        if (at < 0 || at > line.length()) at = line.length();
        String editedLine = new StringBuilder(line).insert(at, key).toString();
        content.set(row, editedLine);
    }

    enum SearchDirection {
        FORWARDS, BACKWARDS
    }

    static SearchDirection searchDirection = SearchDirection.FORWARDS;
    static int lastMatch = -1;

    private static void editorFind() {
        prompt("Search %s (Use ESC/Arrows/Enter)", (query, lastKeyPressed) -> {

            if (query == null || query.isBlank()) {
                searchDirection = SearchDirection.FORWARDS;
                lastMatch = -1;
                return;
            }

            if (lastKeyPressed == ARROW_LEFT || lastKeyPressed == ARROW_UP) {
                searchDirection = SearchDirection.BACKWARDS;
            } else if (lastKeyPressed == ARROW_RIGHT || lastKeyPressed == ARROW_DOWN) {
                searchDirection = SearchDirection.FORWARDS;
            } else {
                searchDirection = SearchDirection.FORWARDS;
                lastMatch = -1;
            }

            int currentIndex = lastMatch;
            for (int i = 0; i < content.size(); i++) {

                currentIndex += searchDirection == SearchDirection.FORWARDS ? 1 : -1;

                if (currentIndex == content.size()) {
                    currentIndex = 0;
                } else if (currentIndex == -1) {
                    currentIndex = content.size() - 1;
                }

                String currentLine = content.get(currentIndex);
                int match = currentLine.indexOf(query);
                if (match != -1) {
                    lastMatch = currentIndex;
                    cursorY = currentIndex;
                    cursorX = match; // not 100% functional
                    offsetY = content.size();
                    break;
                }
            }
        });
    }

    private static void prompt(String message, BiConsumer<String, Integer> consumer) {
        StringBuilder userInput = new StringBuilder();

        while (true) {
            try {
                setStatusMessage(!userInput.isEmpty() ? userInput.toString() : message);
                refreshScreen();
                int key = getKey();

                if (key == '\033' || key == '\r') {
                    setStatusMessage(null);
                    return;
                } else if (key == DEL || key == BACKSPACE || key == control('h')) {
                    if (!userInput.isEmpty()) {
                        userInput.deleteCharAt(userInput.length() - 1);
                    }
                } else if (!Character.isISOControl(key) && key < 128) {
                    userInput.append((char) key);
                }

                consumer.accept(userInput.toString(), key);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static int control(char key) {
        return key & 0x1f;
    }

    private static void exit() {
        System.out.println("\033[2J");
        System.out.println("\033[H");
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, originalAttributes);
        System.exit(0);
    }

    private static void moveCursor(int key) {
        String line = currentLine();
        switch (key) {
            case ARROW_UP -> {
                if (cursorY > 0) {
                    cursorY--;
                }
            }
            case ARROW_DOWN -> {
                if (cursorY < content.size()) {
                    cursorY++;
                }
            }
            case ARROW_LEFT -> {
                if (cursorX > 0) {
                    cursorX--;
                }
            }
            case ARROW_RIGHT -> {
                if (line != null && cursorX < line.length()) {
                    cursorX++;
                }
            }
            case PAGE_UP, PAGE_DOWN -> {
                if (key == PAGE_UP) {
                    moveCursorTopOfScreen();
                } else {
                    moveCursorToBottomScreen();
                }
                for (int i = 0; i < rows; i++) {
                    moveCursor(key == PAGE_UP ? ARROW_UP : ARROW_DOWN);
                }
            }
            case HOME -> cursorX = 0;
            case END -> {
                if (line != null) {
                    cursorX = line.length();
                }
            }
        }
        String newLine = currentLine();
        if (newLine != null && cursorX > newLine.length()) {
            cursorX = newLine.length();
        }
    }

    private static String currentLine() {
        return cursorY < content.size() ? content.get(cursorY) : null;
    }

    private static void moveCursorTopOfScreen() {
        cursorY = offsetY;
    }

    private static void moveCursorToBottomScreen() {
        cursorY = offsetY + rows - 1;
        if (cursorY > content.size()) {
            cursorY = content.size();
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
        scrollPage();
        StringBuilder builder = new StringBuilder();
        moveCaretToTopLeft(builder);
        displayFileContent(builder);
        displayBottomStatusBar(builder);
        refreshCaretPosition(builder);
        System.out.print(builder);
    }

    private static void moveCaretToTopLeft(StringBuilder builder) {
        // Moves the caret to top-left corner
        builder.append("\033[H");
    }

    private static void refreshCaretPosition(StringBuilder builder) {
        // Update the caret position after pressing an arrow key
        builder.append(String.format("\033[%d;%dH", cursorY - offsetY + 1, cursorX - offsetX + 1));
    }

    private static String statusMessage;
    private static void displayBottomStatusBar(StringBuilder builder) {
        String message = statusMessage != null ? statusMessage : "\uD83D\uDCD5 JAYTEXT - v0.0.1 - alpha";
        builder.append("\033[7m")
                .append(message)
                .append(" ".repeat(Math.max(0, columns - message.length())))
                .append("\033[0m");
    }

    public static void setStatusMessage(String statusMessage) {
        Main.statusMessage = statusMessage;
    }

    private static void displayFileContent(StringBuilder builder) {
        for (int i = 0; i < rows; i++) {
            int fileI = offsetY + i;

            if (fileI >= content.size()) {
                builder.append("~");
            } else {
                String line = content.get(fileI);

                int lengthToDraw = line.length() - offsetX;
                if (lengthToDraw < 0) {
                    lengthToDraw = 0;
                }
                if (lengthToDraw > columns) {
                    lengthToDraw = columns;
                }
                if (lengthToDraw > 0) {
                    builder.append(line, offsetX, offsetX + lengthToDraw);
                }
            }
            // Erase in the line content that were previously written
            builder.append("\033[K\r\n");
        }
    }

    private static void initTextEditor() {
        LibC.Winsize winsize = getWindowSize();
        rows = winsize.ws_row - 1;
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
