package cpp;

public class Main {
  public static void main(String... args) throws Exception {
    try {
      cppParser.ProgramContext programCtx = null;
      if (args.length > 0) {
        cppLexer lexer =
            new cppLexer(org.antlr.v4.runtime.CharStreams.fromPath(java.nio.file.Path.of(args[0])));
        org.antlr.v4.runtime.CommonTokenStream tokens =
            new org.antlr.v4.runtime.CommonTokenStream(lexer);
        cppParser parser = new cppParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ParserErrorListener());
        programCtx = parser.program();
      }

      ProgramDef program = new ProgramDef();
      if (programCtx != null) {
        DefinitionBuilder builder = new DefinitionBuilder();
        program = builder.build(programCtx);
      }
      Interpreter interpreter = new Interpreter(program);
      Env sessionEnv = interpreter.runMain();
      interpreter.setReplSessionEnv(sessionEnv);
      interpreter.executeRepl();
    } catch (CompileError | RuntimeError ex) {
      IO.println("Error: " + ex.getMessage());
    }
  }
}
