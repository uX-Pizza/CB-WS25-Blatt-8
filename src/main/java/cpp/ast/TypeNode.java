package cpp.ast;

public class TypeNode extends ASTNode {
    public String name;
    public boolean isRef;

    public TypeNode(String name, boolean isRef) {
        this.name = name;
        this.isRef = isRef;
    }
}
