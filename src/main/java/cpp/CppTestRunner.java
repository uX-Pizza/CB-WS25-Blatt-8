package cpp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CppTestRunner {
  private static final Pattern EXPECT_BLOCK =
      Pattern.compile("/\\*\\s*EXPECT[^\\n]*\\n([\\s\\S]*?)\\*/", Pattern.MULTILINE);

  public static void main(String[] args) throws Exception {
    Path posDir = Path.of("src/main/resources/cpp/tests/pos");
    Path negDir = Path.of("src/main/resources/cpp/tests/neg");

    List<TestResult> results = new ArrayList<>();
    results.addAll(runPositive(posDir));
    results.addAll(runNegative(negDir));

    long failures = results.stream().filter(r -> !r.passed).count();
    for (TestResult result : results) {
      if (!result.passed) {
        IO.println("FAIL " + result.name + ": " + result.message);
      }
    }
    IO.println("\nTotal: " + results.size() + ", Failed: " + failures);
    if (failures > 0) {
      System.exit(1);
    }
  }

  private static List<TestResult> runPositive(Path dir) throws IOException {
    List<TestResult> results = new ArrayList<>();
    for (Path file : listCppFiles(dir)) {
      String expected = parseExpectedOutput(file);
      if (expected == null) {
        results.add(new TestResult(file.getFileName().toString(), false, "Missing EXPECT block"));
        continue;
      }
      try {
        String actual = runProgram(file);
        if (normalize(actual).equals(normalize(expected))) {
          results.add(new TestResult(file.getFileName().toString(), true, ""));
        } else {
          results.add(
              new TestResult(
                  file.getFileName().toString(),
                  false,
                  "Output mismatch\nExpected:\n"
                      + expected
                      + "\nActual:\n"
                      + actual));
        }
      } catch (Exception ex) {
        results.add(
            new TestResult(
                file.getFileName().toString(), false, "Unexpected error: " + ex.getMessage()));
      }
    }
    return results;
  }

  private static List<TestResult> runNegative(Path dir) throws IOException {
    List<TestResult> results = new ArrayList<>();
    for (Path file : listCppFiles(dir)) {
      try {
        runProgram(file);
        results.add(
            new TestResult(
                file.getFileName().toString(), false, "Expected error, but program succeeded"));
      } catch (Exception ex) {
        results.add(new TestResult(file.getFileName().toString(), true, ""));
      }
    }
    return results;
  }

  private static List<Path> listCppFiles(Path dir) throws IOException {
    try (var stream = Files.walk(dir)) {
      return stream.filter(p -> p.toString().endsWith(".cpp")).sorted().toList();
    }
  }

  private static String runProgram(Path file) throws Exception {
    PrintStream originalOut = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    try {
      cppLexer lexer = new cppLexer(org.antlr.v4.runtime.CharStreams.fromPath(file));
      org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
      cppParser parser = new cppParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(new ParserErrorListener());
      cppParser.ProgramContext programCtx = parser.program();

      ProgramDef program = new DefinitionBuilder().build(programCtx);
      Interpreter interpreter = new Interpreter(program);
      interpreter.runMain();
      return buffer.toString(StandardCharsets.UTF_8);
    } finally {
      System.setOut(originalOut);
    }
  }

  private static String parseExpectedOutput(Path file) throws IOException {
    String content = Files.readString(file);
    Matcher matcher = EXPECT_BLOCK.matcher(content);
    if (!matcher.find()) {
      return null;
    }
    String body = matcher.group(1);
    List<String> lines = new ArrayList<>();
    for (String line : body.split("\\R")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        lines.add(trimmed);
      }
    }
    return String.join("\n", lines);
  }

  private static String normalize(String value) {
    String normalized = value.replace("\r\n", "\n").trim();
    return normalized;
  }

  private static class TestResult {
    public final String name;
    public final boolean passed;
    public final String message;

    public TestResult(String name, boolean passed, String message) {
      this.name = name;
      this.passed = passed;
      this.message = message;
    }
  }
}
