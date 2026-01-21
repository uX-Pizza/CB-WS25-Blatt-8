package cpp.interp;

import cpp.error.CompileError;
import cpp.model.ClassDef;
import cpp.model.FunctionDef;
import cpp.model.MethodDef;
import cpp.model.ParamDef;
import cpp.model.ProgramDef;
import cpp.model.Type;
import cpp.runtime.Env;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dispatch {
  private final ProgramDef program;
  private final ObjectModel objectModel;

  public Dispatch(ProgramDef program, ObjectModel objectModel) {
    this.program = program;
    this.objectModel = objectModel;
  }

  public FunctionDef resolveMain() {
    List<FunctionDef> mains = program.functions.get("main");
    if (mains == null) {
      return null;
    }
    FunctionDef selected = null;
    for (FunctionDef candidate : mains) {
      if (!candidate.params.isEmpty()) {
        continue;
      }
      if (!candidate.returnType.equals(Type.intType(false))
          && !candidate.returnType.equals(Type.voidType())) {
        continue;
      }
      if (selected != null) {
        throw new CompileError("Multiple main() definitions");
      }
      selected = candidate;
    }
    return selected;
  }

  public FunctionDef selectFunction(List<FunctionDef> candidates, List<ArgInfo> args) {
    List<FunctionDef> matches = new ArrayList<>();
    for (FunctionDef fn : candidates) {
      if (matchesParams(fn.params, args)) {
        matches.add(fn);
      }
    }
    if (matches.isEmpty()) {
      throw new CompileError("No matching function overload");
    }
    if (matches.size() > 1) {
      throw new CompileError("Ambiguous function call");
    }
    return matches.get(0);
  }

  public MethodDef selectMethod(ClassDef staticClass, String name, List<ArgInfo> args) {
    Map<String, MethodDef> candidates = new HashMap<>();
    for (Map.Entry<String, MethodDef> entry : staticClass.vtable.entrySet()) {
      if (entry.getValue().name.equals(name)) {
        candidates.put(entry.getKey(), entry.getValue());
      }
    }
    List<MethodDef> matches = new ArrayList<>();
    for (MethodDef method : candidates.values()) {
      if (matchesParams(method.params, args)) {
        matches.add(method);
      }
    }
    if (matches.isEmpty()) {
      throw new CompileError("No matching method: " + name);
    }
    if (matches.size() > 1) {
      throw new CompileError("Ambiguous method call: " + name);
    }
    return matches.get(0);
  }

  public boolean isVirtualInStatic(ClassDef staticClass, String signature) {
    ClassDef current = staticClass;
    while (current != null) {
      MethodDef method = current.vtable.get(signature);
      if (method != null && method.isVirtual) {
        return true;
      }
      current = current.baseClass;
    }
    return false;
  }

  public void bindParams(
      Env env, List<ParamDef> params, List<ArgInfo> args, ClassDef currentClass) {
    for (int i = 0; i < params.size(); i++) {
      ParamDef param = params.get(i);
      ArgInfo arg = args.get(i);
      if (currentClass != null) {
        if (objectModel.collectFieldNames(currentClass).contains(param.name)) {
          throw new CompileError("Parameter shadows field: " + param.name);
        }
      }
      if (param.type.isRef) {
        env.define(param.name, objectModel.createRefSlot(param.type, arg.result.slot));
      } else {
        env.define(param.name, objectModel.createValueSlot(param.type, arg.result.value));
      }
    }
  }

  public void ensureUniqueFunction(FunctionDef def) {
    List<FunctionDef> existing = program.functions.get(def.name);
    if (existing == null) {
      return;
    }
    String signature = cpp.sema.SignatureUtil.signature(def.name, def.params);
    for (FunctionDef other : existing) {
      if (cpp.sema.SignatureUtil.signature(other.name, other.params).equals(signature)) {
        throw new CompileError("Function already defined: " + signature);
      }
    }
  }

  public boolean matchesParams(List<ParamDef> params, List<ArgInfo> args) {
    if (params.size() != args.size()) {
      return false;
    }
    for (int i = 0; i < params.size(); i++) {
      ParamDef param = params.get(i);
      ArgInfo arg = args.get(i);
      if (param.type.isRef) {
        if (!arg.result.isLValue) {
          return false;
        }
        if (!param.type.withoutRef().equals(arg.result.type)) {
          return false;
        }
      } else {
        if (!param.type.equals(arg.result.type)) {
          return false;
        }
      }
    }
    return true;
  }
}
