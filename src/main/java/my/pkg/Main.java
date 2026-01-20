package my.pkg;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

public class Main {
  static void main(String... args) {
    System.out.println("Hello World!");

    // Einlesen über Konsole/Prompt
    System.out.print("expr?> ");
    String input;
    try {
      input = new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }

    // Demonstriert den Einsatz von Packages und Grammatiken
    HelloPackageLexer lexer = new HelloPackageLexer(CharStreams.fromString(input));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    HelloPackageParser parser = new HelloPackageParser(tokens);

    ParseTree tree = parser.start(); // Start-Regel
    System.out.println(tree.toStringTree(parser));
  }
}
