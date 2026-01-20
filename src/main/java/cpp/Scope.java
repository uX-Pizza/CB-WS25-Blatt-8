package cpp;

import java.util.*;

public class Scope {
    private Map<String, Symbol> symbols = new HashMap<>();
    public Scope parent;

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public boolean define(Symbol sym) {
        if (symbols.containsKey(sym.name)) return false;
        symbols.put(sym.name, sym);
        return true;
    }

    public Symbol resolve(String name) {
        if (symbols.containsKey(name)) return symbols.get(name);
        if (parent != null) return parent.resolve(name);
        return null;
    }
}
