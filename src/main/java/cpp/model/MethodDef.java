package cpp.model;

import cpp.ast.BlockNode;
import java.util.List;

public class MethodDef {
  public final String name;
  public final Type returnType;
  public final List<ParamDef> params;
  public final BlockNode body;
  public final boolean isVirtual;
  public final String declaredIn;

  public MethodDef(
      String name,
      Type returnType,
      List<ParamDef> params,
      BlockNode body,
      boolean isVirtual,
      String declaredIn) {
    this.name = name;
    this.returnType = returnType;
    this.params = params;
    this.body = body;
    this.isVirtual = isVirtual;
    this.declaredIn = declaredIn;
  }
}
