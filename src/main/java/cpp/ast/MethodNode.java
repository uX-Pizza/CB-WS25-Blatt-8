package cpp.ast;

import java.util.List;

public class MethodNode extends ClassMemberNode {
  public final boolean isVirtual;
  public final TypeNode returnType;
  public final String name;
  public final List<ParamNode> params;
  public final BlockNode body;

  public MethodNode(
      boolean isVirtual, TypeNode returnType, String name, List<ParamNode> params, BlockNode body) {
    this.isVirtual = isVirtual;
    this.returnType = returnType;
    this.name = name;
    this.params = params;
    this.body = body;
  }
}
