package cpp.ast;

public class ReturnStmtNode extends StmtNode {
  public final ExprNode value;

  public ReturnStmtNode(ExprNode value) {
    this.value = value;
  }
}
