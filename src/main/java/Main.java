import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class Main {
  static void main(String... args) throws IOException, URISyntaxException {
    IO.println("Hello World!");

    Path path = Path.of("src/main/resources/cpp/tests/pos/GOLD01_basics.cpp");

    cppLexer cpplexer = new cppLexer(CharStreams.fromPath(path));
    CommonTokenStream tokens = new CommonTokenStream(cpplexer);
    cppParser parser = new cppParser(tokens);

    ParseTree tree = parser.program(); // Start-Regel
    IO.println(tree.toStringTree(parser));
    /*
    // Einlesen über den Classpath
    IO.readln("next?> ");
    try (InputStream in = Main.class.getResourceAsStream("/cpp/vars.cpp")) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      IO.println("\n\n/cpp/vars.cpp");
      IO.println(text);
    }

    // Einlesen über Dateisystem
    IO.readln("next?> ");
    URL url = Main.class.getResource("/cpp/expr.cpp");
    String txt = Files.readString(Path.of(url.toURI()), StandardCharsets.UTF_8);
    IO.println("\n\n/cpp/expr.cpp");
    IO.println(txt);
    */
  }
}
