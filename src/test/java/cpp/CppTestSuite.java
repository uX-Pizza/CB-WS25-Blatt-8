package cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cpp.antlr.cppLexer;
import cpp.antlr.cppParser;
import cpp.ast.ProgramNode;
import cpp.interp.Interpreter;
import cpp.model.ProgramDef;
import cpp.sema.ASTBuilder;
import cpp.sema.DefinitionBuilder;
import cpp.util.ParserErrorListener;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CppTestSuite {
  private static final Pattern EXPECT_BLOCK =
      Pattern.compile("/\\*\\s*EXPECT[^\\n]*\\n([\\s\\S]*?)\\*/", Pattern.MULTILINE);

  @Test
  void sanity() {
    new Interpreter(new ProgramDef());
  }

  @DisplayName("Positive tests")
  @ParameterizedTest(name = "{0}")
  @MethodSource("positiveTests")
  void runPositiveTests(Path file) throws Exception {
    String expected = parseExpectedOutput(file);
    assertNotNull(expected, "Missing EXPECT block in " + file.getFileName());
    String actual = runProgram(file);
    assertEquals(normalize(expected), normalize(actual), "Output mismatch for " + file);
  }

  @DisplayName("Negative tests")
  @ParameterizedTest(name = "{0}")
  @MethodSource("negativeTests")
  void runNegativeTests(Path file) {
    assertThrows(RuntimeException.class, () -> runProgram(file));
  }

  static Stream<Path> positiveTests() throws IOException {
    return listCppFiles(Path.of("src/main/resources/cpp/tests/pos")).stream();
  }

  static Stream<Path> negativeTests() throws IOException {
    return listCppFiles(Path.of("src/main/resources/cpp/tests/neg")).stream();
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
      org.antlr.v4.runtime.CommonTokenStream tokens =
          new org.antlr.v4.runtime.CommonTokenStream(lexer);
      cppParser parser = new cppParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(new ParserErrorListener());
      cppParser.ProgramContext programCtx = parser.program();

      ProgramNode programNode = (ProgramNode) new ASTBuilder().visit(programCtx);
      ProgramDef program = new DefinitionBuilder().build(programNode);
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
    return value.replace("\r\n", "\n").trim();
  }
}
