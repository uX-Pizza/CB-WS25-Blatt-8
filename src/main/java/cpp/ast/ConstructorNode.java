package cpp.ast;

import java.util.List;

public class ConstructorNode extends ClassMemberNode {
  public final String name;
  public final List<ParamNode> params;
  public final BlockNode body;

  public ConstructorNode(String name, List<ParamNode> params, BlockNode body) {
    this.name = name;
    this.params = params;
    this.body = body;
  }
}
