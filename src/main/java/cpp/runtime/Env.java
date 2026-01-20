package cpp.runtime;

import java.util.HashMap;
import java.util.Map;

public class Env {
  private final Env parent;
  private final Map<String, VarSlot> vars = new HashMap<>();

  public Env(Env parent) {
    this.parent = parent;
  }

  public Env getParent() {
    return parent;
  }

  public boolean define(String name, VarSlot slot) {
    if (vars.containsKey(name)) {
      return false;
    }
    vars.put(name, slot);
    return true;
  }

  public VarSlot resolve(String name) {
    VarSlot slot = vars.get(name);
    if (slot != null) {
      return slot;
    }
    if (parent != null) {
      return parent.resolve(name);
    }
    return null;
  }

  public boolean containsInCurrentScope(String name) {
    return vars.containsKey(name);
  }
}
