package cpp.ast;

public class WhileStmtNode extends StmtNode {
  public final ExprNode condition;
  public final BlockNode body;

  public WhileStmtNode(ExprNode condition, BlockNode body) {
    this.condition = condition;
    this.body = body;
  }
}
