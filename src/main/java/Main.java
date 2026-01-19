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

    // visitor patterns incomplete
    // ASTBuilder astBuilder = new ASTBuilder();
    //ProgramNode ast = (ProgramNode) astBuilder.visit(tree);
  }
}
