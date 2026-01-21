package cpp.ast;

public class IfStmtNode extends StmtNode {
  public final ExprNode condition;
  public final BlockNode thenBlock;
  public final BlockNode elseBlock;

  public IfStmtNode(ExprNode condition, BlockNode thenBlock, BlockNode elseBlock) {
    this.condition = condition;
    this.thenBlock = thenBlock;
    this.elseBlock = elseBlock;
  }
}
