package cpp.sema;

import cpp.ast.TypeNode;

public class Symbol {
    public String name;
    public TypeNode type;

    public Symbol(String name, TypeNode type) {
        this.name = name;
        this.type = type;
    }
}
