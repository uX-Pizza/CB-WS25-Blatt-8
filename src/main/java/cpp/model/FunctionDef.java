package cpp.model;

import cpp.ast.BlockNode;
import java.util.List;

public class FunctionDef {
  public final String name;
  public final Type returnType;
  public final List<ParamDef> params;
  public final BlockNode body;
  public final boolean isBuiltin;

  public FunctionDef(String name, Type returnType, List<ParamDef> params, BlockNode body) {
    this(name, returnType, params, body, false);
  }

  public FunctionDef(
      String name, Type returnType, List<ParamDef> params, BlockNode body, boolean isBuiltin) {
    this.name = name;
    this.returnType = returnType;
    this.params = params;
    this.body = body;
    this.isBuiltin = isBuiltin;
  }
}
