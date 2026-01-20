package cpp;

import java.util.LinkedHashMap;
import java.util.Map;

public class Instance {
  public final ClassDef classDef;
  public final Map<String, VarSlot> fields = new LinkedHashMap<>();

  public Instance(ClassDef classDef) {
    this.classDef = classDef;
  }
}
