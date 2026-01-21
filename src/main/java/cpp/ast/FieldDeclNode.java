package cpp.ast;

public class FieldDeclNode extends ClassMemberNode {
  public final TypeNode type;
  public final String name;

  public FieldDeclNode(TypeNode type, String name) {
    this.type = type;
    this.name = name;
  }
}
