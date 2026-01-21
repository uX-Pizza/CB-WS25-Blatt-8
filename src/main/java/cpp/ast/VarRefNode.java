package cpp.ast;

public class VarRefNode extends ExprNode {
  public final String name;

  public VarRefNode(String name) {
    this.name = name;
  }
}
