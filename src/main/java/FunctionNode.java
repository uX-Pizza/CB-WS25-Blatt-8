import java.util.List;

public class FunctionNode extends ASTNode {
    public TypeNode returnType;
    public String name;
    public List<ParamNode> params;
    public BlockNode body;

    public FunctionNode(TypeNode returnType, String name,
                        List<ParamNode> params, BlockNode body) {
        this.returnType = returnType;
        this.name = name;
        this.params = params;
        this.body = body;
    }
}
