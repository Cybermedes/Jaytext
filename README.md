# Jaytext

A simple terminal text editor, similar to Vim, Nano and others, made in Java. It doesn't have the advanced features like VCS, auto complete, regex, or other functionalities.
But it does the basic operations, move cursor, delete, update and insert characters and lines.

## Dependencies:

- JDK 21
- IDE
- Terminal - preferable with UTF-8 support, the consoles from some IDEs may not render or display some of the features correctly. Sine JDK 11 you can lunch single-file source
code programs without the need of compiling it first. This works by the `java` launcher automatically invoking the compiler and storing the compiled code in-memory.
- Java Native Support (JNA): https://github.com/java-native-access/jna?tab=readme-ov-file - Download the jar file and add it to your project library. No dependency
  management tools like Maven or Gradle required

  ## How to run Jaytext?

  Go to the terminal and type:
  ```java
  java -cp jna-5.14.0.jar path/to/Main.java
  ```
