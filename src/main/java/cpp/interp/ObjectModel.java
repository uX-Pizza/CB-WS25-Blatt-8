package cpp.interp;

import cpp.error.CompileError;
import cpp.model.ClassDef;
import cpp.model.ConstructorDef;
import cpp.model.FieldDef;
import cpp.model.ParamDef;
import cpp.model.ProgramDef;
import cpp.model.Type;
import cpp.runtime.Instance;
import cpp.runtime.Value;
import cpp.runtime.VarSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ObjectModel {
  private final ProgramDef program;
  private StmtExecutor stmtExecutor;

  public ObjectModel(ProgramDef program) {
    this.program = program;
  }

  public void setStmtExecutor(StmtExecutor stmtExecutor) {
    this.stmtExecutor = stmtExecutor;
  }

  public Instance createInstance(ClassDef classDef, List<ArgInfo> args) {
    ConstructorDef ctor = selectConstructor(classDef, args);
    Instance instance = new Instance(classDef);
    initializeFields(instance, classDef);
    if (classDef.baseClass != null) {
      callBaseDefaultConstructor(instance, classDef.baseClass);
    }
    if (ctor.body != null) {
      ExecContext ctorContext =
          new ExecContext(new cpp.runtime.Env(null), instance, classDef);
      stmtExecutor.bindParams(ctorContext.env, ctor.params, args, classDef);
      try {
        stmtExecutor.executeBlock((cpp.antlr.cppParser.BlockContext) ctor.body, ctorContext, false);
      } catch (cpp.runtime.ReturnSignal signal) {
        throw new CompileError("Return not allowed in constructor");
      }
    } else if (ctor.isSyntheticCopy) {
      Instance source = (Instance) args.get(0).result.value.data;
      copyInto(instance, source, classDef.name);
    }
    return instance;
  }

  public Value defaultValue(Type type) {
    if (type.isRef) {
      throw new CompileError("Reference must be initialized");
    }
    return switch (type.kind) {
      case BOOL -> Value.boolValue(false);
      case INT -> Value.intValue(0);
      case CHAR -> Value.charValue('\0');
      case STRING -> Value.stringValue("");
      case VOID -> Value.voidValue();
      case CLASS -> new Value(type, createInstance(program.classes.get(type.className), List.of()));
    };
  }

  public Value coerceValue(Value value, Type targetType) {
    if (targetType.isRef) {
      throw new CompileError("Cannot assign to reference directly");
    }
    if (targetType.equals(value.type)) {
      if (targetType.isClass()) {
        Instance instance = (Instance) value.data;
        return new Value(targetType, copyInstance(instance, targetType.className));
      }
      return value;
    }
    if (targetType.isClass() && value.type.isClass()) {
      if (isDerivedFrom(value.type.className, targetType.className)) {
        Instance instance = (Instance) value.data;
        return new Value(targetType, sliceInstance(instance, targetType.className));
      }
    }
    throw new CompileError("Type mismatch: expected " + targetType + " got " + value.type);
  }

  public void assignValueToSlot(VarSlot slot, Type targetType, cpp.runtime.EvalResult right) {
    Value value = right.value;
    if (targetType.isClass()) {
      if (!value.type.isClass()) {
        throw new CompileError("Type mismatch: expected class " + targetType + " got " + value.type);
      }
      if (!isDerivedFrom(value.type.className, targetType.className)) {
        throw new CompileError("Type mismatch: expected " + targetType + " got " + value.type);
      }
      Instance rhs = (Instance) value.data;
      Instance target = (Instance) slot.get().data;
      copyInto(target, rhs, targetType.className);
      return;
    }
    expectType(targetType, value.type, "assignment");
    slot.set(value);
  }

  public boolean isDerivedFrom(String derived, String base) {
    if (derived.equals(base)) {
      return true;
    }
    ClassDef current = program.classes.get(derived);
    while (current != null && current.baseClass != null) {
      if (current.baseClass.name.equals(base)) {
        return true;
      }
      current = current.baseClass;
    }
    return false;
  }

  public Set<String> collectFieldNames(ClassDef classDef) {
    Set<String> names = new HashSet<>();
    ClassDef current = classDef;
    while (current != null) {
      for (FieldDef field : current.fields) {
        names.add(field.name);
      }
      current = current.baseClass;
    }
    return names;
  }

  public boolean hasField(ClassDef classDef, String name) {
    ClassDef current = classDef;
    while (current != null) {
      for (FieldDef field : current.fields) {
        if (field.name.equals(name)) {
          return true;
        }
      }
      current = current.baseClass;
    }
    return false;
  }

  public VarSlot createRefSlot(Type type, VarSlot target) {
    return VarSlot.refSlot(type, target);
  }

  public VarSlot createValueSlot(Type type, Value value) {
    return new VarSlot(type, coerceValue(value, type));
  }

  private void initializeFields(Instance instance, ClassDef classDef) {
    if (classDef.baseClass != null) {
      initializeFields(instance, classDef.baseClass);
    }
    for (FieldDef field : classDef.fields) {
      Value value = defaultValue(field.type);
      instance.fields.put(field.name, new VarSlot(field.type, value));
    }
  }

  private void callBaseDefaultConstructor(Instance instance, ClassDef baseClass) {
    if (baseClass.baseClass != null) {
      callBaseDefaultConstructor(instance, baseClass.baseClass);
    }
    ConstructorDef baseCtor = selectConstructor(baseClass, List.of());
    if (baseCtor.body == null) {
      return;
    }
    ExecContext ctorContext = new ExecContext(new cpp.runtime.Env(null), instance, baseClass);
    try {
      stmtExecutor.executeBlock((cpp.antlr.cppParser.BlockContext) baseCtor.body, ctorContext, false);
    } catch (cpp.runtime.ReturnSignal signal) {
      throw new CompileError("Return not allowed in constructor");
    }
  }

  private ConstructorDef selectConstructor(ClassDef classDef, List<ArgInfo> args) {
    List<ConstructorDef> matches = new ArrayList<>();
    for (ConstructorDef ctor : classDef.constructors) {
      if (matchesParams(ctor.params, args)) {
        matches.add(ctor);
      }
    }
    if (matches.isEmpty()) {
      ConstructorDef copyCtor = trySyntheticCopyCtor(classDef, args);
      if (copyCtor != null) {
        return copyCtor;
      }
      throw new CompileError("No matching constructor for class: " + classDef.name);
    }
    if (matches.size() > 1) {
      throw new CompileError("Ambiguous constructor call for class: " + classDef.name);
    }
    return matches.get(0);
  }

  private ConstructorDef trySyntheticCopyCtor(ClassDef classDef, List<ArgInfo> args) {
    if (args.size() != 1) {
      return null;
    }
    ArgInfo arg = args.get(0);
    if (!arg.result.type.isClass()) {
      return null;
    }
    if (!arg.result.type.className.equals(classDef.name)) {
      return null;
    }
    ParamDef param = new ParamDef(Type.classType(classDef.name, false), "other");
    return new ConstructorDef(classDef.name, List.of(param), null, true);
  }

  private boolean matchesParams(List<ParamDef> params, List<ArgInfo> args) {
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

  private Instance copyInstance(Instance instance, String targetClassName) {
    Instance copy = new Instance(program.classes.get(targetClassName));
    initializeFields(copy, copy.classDef);
    copyInto(copy, instance, targetClassName);
    return copy;
  }

  private Instance sliceInstance(Instance instance, String targetClassName) {
    Instance copy = new Instance(program.classes.get(targetClassName));
    initializeFields(copy, copy.classDef);
    copyInto(copy, instance, targetClassName);
    return copy;
  }

  public void copyInto(Instance target, Instance source, String targetClassName) {
    ClassDef targetClass = program.classes.get(targetClassName);
    ClassDef current = targetClass;
    while (current != null) {
      for (FieldDef field : current.fields) {
        VarSlot src = source.fields.get(field.name);
        if (src == null) {
          continue;
        }
        Value copied = coerceValue(src.get(), field.type);
        target.fields.get(field.name).set(copied);
      }
      current = current.baseClass;
    }
  }

  public void expectType(Type expected, Type actual, String context) {
    if (!expected.equals(actual)) {
      throw new CompileError("Type mismatch in " + context + ": expected " + expected + " got " + actual);
    }
  }
}
