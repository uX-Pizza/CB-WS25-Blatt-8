package cpp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class IO {
  private static final BufferedReader IN = new BufferedReader(new InputStreamReader(System.in));

  public static void print(String value) {
    System.out.print(value);
  }

  public static void println(String value) {
    System.out.println(value);
  }

  public static String readln(String prompt) {
    if (prompt != null && !prompt.isEmpty()) {
      print(prompt);
    }
    try {
      return IN.readLine();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read input", e);
    }
  }
}
