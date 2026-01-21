package cpp.ast;

public class ExprStmtNode extends StmtNode {
  public final ExprNode expr;

  public ExprStmtNode(ExprNode expr) {
    this.expr = expr;
  }
}
