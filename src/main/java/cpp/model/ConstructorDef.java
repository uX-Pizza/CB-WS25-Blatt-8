package cpp.model;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

public class ConstructorDef {
  public final String className;
  public final List<ParamDef> params;
  public final ParseTree body;
  public final boolean isSyntheticCopy;

  public ConstructorDef(String className, List<ParamDef> params, ParseTree body) {
    this(className, params, body, false);
  }

  public ConstructorDef(
      String className, List<ParamDef> params, ParseTree body, boolean isSyntheticCopy) {
    this.className = className;
    this.params = params;
    this.body = body;
    this.isSyntheticCopy = isSyntheticCopy;
  }
}
