package cpp.ast;

import java.util.ArrayList;
import java.util.List;

public class ClassDefNode extends ASTNode {
  public final String name;
  public final String baseName;
  public final List<ClassMemberNode> members = new ArrayList<>();

  public ClassDefNode(String name, String baseName) {
    this.name = name;
    this.baseName = baseName;
  }
}
