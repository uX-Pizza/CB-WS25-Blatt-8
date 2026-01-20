import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

public class ConstructorDef {
  public final String className;
  public final List<ParamDef> params;
  public final ParseTree body;

  public ConstructorDef(String className, List<ParamDef> params, ParseTree body) {
    this.className = className;
    this.params = params;
    this.body = body;
  }
}
