package cpp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProgramDef {
  public final Map<String, ClassDef> classes = new HashMap<>();
  public final Map<String, List<FunctionDef>> functions = new HashMap<>();

  public void addFunction(FunctionDef function) {
    functions.computeIfAbsent(function.name, name -> new ArrayList<>()).add(function);
  }
}
