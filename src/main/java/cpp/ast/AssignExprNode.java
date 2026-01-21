package cpp.ast;

public class AssignExprNode extends ExprNode {
  public final ExprNode target;
  public final ExprNode value;

  public AssignExprNode(ExprNode target, ExprNode value) {
    this.target = target;
    this.value = value;
  }
}
