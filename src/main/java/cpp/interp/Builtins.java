package cpp.interp;

import cpp.error.CompileError;
import cpp.model.FunctionDef;
import cpp.model.ParamDef;
import cpp.model.ProgramDef;
import cpp.model.Type;
import cpp.runtime.Value;
import cpp.util.IO;
import java.util.List;

public class Builtins {
  public void register(ProgramDef program) {
    addBuiltin(
        program, "print_bool", Type.voidType(), List.of(new ParamDef(Type.boolType(false), "v")));
    addBuiltin(
        program, "print_int", Type.voidType(), List.of(new ParamDef(Type.intType(false), "v")));
    addBuiltin(
        program, "print_char", Type.voidType(), List.of(new ParamDef(Type.charType(false), "v")));
    addBuiltin(
        program,
        "print_string",
        Type.voidType(),
        List.of(new ParamDef(Type.stringType(false), "v")));
  }

  public void execute(String name, List<ArgInfo> args) {
    Value value = args.get(0).result.value;
    switch (name) {
      case "print_bool" -> IO.println(((boolean) value.data) ? "1" : "0");
      case "print_int" -> IO.println(Integer.toString((int) value.data));
      case "print_char" -> IO.println(Character.toString((char) value.data));
      case "print_string" -> IO.println((String) value.data);
      default -> throw new CompileError("Unknown builtin: " + name);
    }
  }

  public void printValue(Value value) {
    if (value.type.kind == Type.Kind.BOOL) {
      IO.println(((boolean) value.data) ? "1" : "0");
    } else if (value.type.kind == Type.Kind.INT) {
      IO.println(Integer.toString((int) value.data));
    } else if (value.type.kind == Type.Kind.CHAR) {
      IO.println(Character.toString((char) value.data));
    } else if (value.type.kind == Type.Kind.STRING) {
      IO.println((String) value.data);
    }
  }

  private void addBuiltin(ProgramDef program, String name, Type returnType, List<ParamDef> params) {
    FunctionDef def = new FunctionDef(name, returnType, params, null, true);
    program.addFunction(def);
  }
}
