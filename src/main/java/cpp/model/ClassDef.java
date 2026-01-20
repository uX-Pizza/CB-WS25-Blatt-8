package cpp.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassDef {
  public final String name;
  public final String baseName;
  public ClassDef baseClass;
  public final List<FieldDef> fields = new ArrayList<>();
  public final List<MethodDef> methods = new ArrayList<>();
  public final List<ConstructorDef> constructors = new ArrayList<>();
  public final Map<String, MethodDef> vtable = new HashMap<>();

  public ClassDef(String name, String baseName) {
    this.name = name;
    this.baseName = baseName;
  }
}
