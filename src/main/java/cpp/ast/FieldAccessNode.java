package cpp.ast;

public class FieldAccessNode extends ExprNode {
  public final ExprNode receiver;
  public final String name;

  public FieldAccessNode(ExprNode receiver, String name) {
    this.receiver = receiver;
    this.name = name;
  }
}
