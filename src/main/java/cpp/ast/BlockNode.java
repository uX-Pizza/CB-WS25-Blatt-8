package cpp.ast;

import java.util.ArrayList;
import java.util.List;

public class BlockNode extends ASTNode {
  public List<StmtNode> statements = new ArrayList<>();
}
