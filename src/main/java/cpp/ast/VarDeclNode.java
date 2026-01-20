package cpp.ast;

public class VarDeclNode extends StmtNode {
    public TypeNode type;
    public String name;
    public ExprNode init;

    public VarDeclNode(TypeNode type, String name, ExprNode init) {
        this.type = type;
        this.name = name;
        this.init = init;
    }
}
