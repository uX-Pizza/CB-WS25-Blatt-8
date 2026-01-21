package cpp.ast;

public class UnaryExprNode extends ExprNode {
  public final String op;
  public final ExprNode expr;

  public UnaryExprNode(String op, ExprNode expr) {
    this.op = op;
    this.expr = expr;
  }
}
