public class TypeResolver {
  private final ProgramDef program;

  public TypeResolver(ProgramDef program) {
    this.program = program;
  }

  public Type parse(cppParser.TypeContext ctx) {
    String base = ctx.baseType().getText();
    boolean isRef = ctx.ref() != null;
    if ("int".equals(base)) {
      return Type.intType(isRef);
    }
    if ("bool".equals(base)) {
      return Type.boolType(isRef);
    }
    if ("char".equals(base)) {
      return Type.charType(isRef);
    }
    if ("string".equals(base)) {
      return Type.stringType(isRef);
    }
    if ("void".equals(base)) {
      if (isRef) {
        throw new CompileError("void& is not allowed");
      }
      return Type.voidType();
    }
    if (!program.classes.containsKey(base)) {
      throw new CompileError("Unknown class type: " + base);
    }
    return Type.classType(base, isRef);
  }
}
