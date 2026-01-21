package cpp.ast;

import java.util.List;

public class CallExprNode extends ExprNode {
  public final String name;
  public final List<ExprNode> args;

  public CallExprNode(String name, List<ExprNode> args) {
    this.name = name;
    this.args = args;
  }
}
