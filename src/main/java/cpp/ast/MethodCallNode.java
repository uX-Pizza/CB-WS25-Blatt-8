package cpp.ast;

import java.util.List;

public class MethodCallNode extends ExprNode {
  public final ExprNode receiver;
  public final String name;
  public final List<ExprNode> args;

  public MethodCallNode(ExprNode receiver, String name, List<ExprNode> args) {
    this.receiver = receiver;
    this.name = name;
    this.args = args;
  }
}
