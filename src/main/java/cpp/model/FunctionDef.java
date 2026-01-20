package cpp.model;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

public class FunctionDef {
  public final String name;
  public final Type returnType;
  public final List<ParamDef> params;
  public final ParseTree body;
  public final boolean isBuiltin;

  public FunctionDef(String name, Type returnType, List<ParamDef> params, ParseTree body) {
    this(name, returnType, params, body, false);
  }

  public FunctionDef(
      String name, Type returnType, List<ParamDef> params, ParseTree body, boolean isBuiltin) {
    this.name = name;
    this.returnType = returnType;
    this.params = params;
    this.body = body;
    this.isBuiltin = isBuiltin;
  }
}
