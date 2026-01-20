package cpp;

import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

public class MethodDef {
  public final String name;
  public final Type returnType;
  public final List<ParamDef> params;
  public final ParseTree body;
  public final boolean isVirtual;
  public final String declaredIn;

  public MethodDef(
      String name,
      Type returnType,
      List<ParamDef> params,
      ParseTree body,
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
