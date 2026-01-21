package cpp.ast;

import java.util.ArrayList;
import java.util.List;

public class BlockNode extends StmtNode {
  public List<StmtNode> statements = new ArrayList<>();
}
