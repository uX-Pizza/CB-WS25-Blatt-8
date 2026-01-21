package cpp.model;

import cpp.ast.BlockNode;
import java.util.List;

public class ConstructorDef {
  public final String className;
  public final List<ParamDef> params;
  public final BlockNode body;
  public final boolean isSyntheticCopy;

  public ConstructorDef(String className, List<ParamDef> params, BlockNode body) {
    this(className, params, body, false);
  }

  public ConstructorDef(
      String className, List<ParamDef> params, BlockNode body, boolean isSyntheticCopy) {
    this.className = className;
    this.params = params;
    this.body = body;
    this.isSyntheticCopy = isSyntheticCopy;
  }
}
