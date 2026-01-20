package cpp.ast;

public class ParamNode extends ASTNode {
  public TypeNode type;
  public String name;

  public ParamNode(TypeNode type, String name) {
    this.type = type;
    this.name = name;
  }
}
