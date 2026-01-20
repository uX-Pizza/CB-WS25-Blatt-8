package cpp.interp;

import cpp.model.ClassDef;
import cpp.runtime.Env;
import cpp.runtime.Instance;

public class ExecContext {
  public final Env env;
  public final Instance instance;
  public final ClassDef currentClass;

  public ExecContext(Env env, Instance instance, ClassDef currentClass) {
    this.env = env;
    this.instance = instance;
    this.currentClass = currentClass;
  }
}
