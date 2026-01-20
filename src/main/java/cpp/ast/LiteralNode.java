package cpp.ast;

public class LiteralNode extends ExprNode {
  public Object value;

  public LiteralNode(Object value) {
    this.value = value;
  }
}
