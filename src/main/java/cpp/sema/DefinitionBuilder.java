package cpp.sema;

import cpp.ast.ClassDefNode;
import cpp.ast.ClassMemberNode;
import cpp.ast.ConstructorNode;
import cpp.ast.FieldDeclNode;
import cpp.ast.FunctionNode;
import cpp.ast.MethodNode;
import cpp.ast.ParamNode;
import cpp.ast.ProgramNode;
import cpp.ast.TypeNode;
import cpp.error.CompileError;
import cpp.model.ClassDef;
import cpp.model.ConstructorDef;
import cpp.model.FieldDef;
import cpp.model.FunctionDef;
import cpp.model.MethodDef;
import cpp.model.ParamDef;
import cpp.model.ProgramDef;
import cpp.model.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefinitionBuilder {
  private final ProgramDef program = new ProgramDef();

  public ProgramDef build(ProgramNode programNode) {
    for (var decl : programNode.declarations) {
      if (decl instanceof ClassDefNode classDef) {
        registerClassHeader(classDef);
      }
    }

    for (var decl : programNode.declarations) {
      if (decl instanceof FunctionNode functionDef) {
        registerFunction(functionDef);
      }
    }

    for (var decl : programNode.declarations) {
      if (decl instanceof ClassDefNode classDef) {
        populateClassMembers(classDef);
      }
    }

    resolveBaseClasses();
    validateFieldNames();
    buildVtables();
    ensureDefaultConstructors();

    return program;
  }

  private void registerClassHeader(ClassDefNode classDef) {
    String name = classDef.name;
    String baseName = classDef.baseName;
    if (program.classes.containsKey(name)) {
      throw new CompileError("Class already defined: " + name);
    }
    program.classes.put(name, new ClassDef(name, baseName));
  }

  private void registerFunction(FunctionNode functionDef) {
    Type returnType = parseType(functionDef.returnType);
    if (returnType.isRef) {
      throw new CompileError("Reference return types are not allowed: " + returnType);
    }
    String name = functionDef.name;
    List<ParamDef> params = parseParams(functionDef.params);
    FunctionDef def = new FunctionDef(name, returnType, params, functionDef.body);
    ensureUniqueFunction(def);
    program.addFunction(def);
  }

  private void populateClassMembers(ClassDefNode classDefNode) {
    ClassDef classDef = program.classes.get(classDefNode.name);
    for (ClassMemberNode member : classDefNode.members) {
      if (member instanceof FieldDeclNode fieldDecl) {
        addField(classDef, fieldDecl);
      } else if (member instanceof MethodNode methodDef) {
        addMethod(classDef, methodDef);
      } else if (member instanceof ConstructorNode ctorDef) {
        addConstructor(classDef, ctorDef);
      }
    }
  }

  private void addField(ClassDef classDef, FieldDeclNode fieldDecl) {
    Type type = parseType(fieldDecl.type);
    if (type.isRef) {
      throw new CompileError("Reference fields are not allowed: " + type);
    }
    if (type.isVoid()) {
      throw new CompileError("Field type cannot be void");
    }
    String name = fieldDecl.name;
    for (FieldDef field : classDef.fields) {
      if (field.name.equals(name)) {
        throw new CompileError("Field already defined: " + name);
      }
    }
    classDef.fields.add(new FieldDef(type, name));
  }

  private void addMethod(ClassDef classDef, MethodNode methodDef) {
    boolean isVirtual = methodDef.isVirtual;
    Type returnType = parseType(methodDef.returnType);
    if (returnType.isRef) {
      throw new CompileError("Reference return types are not allowed: " + returnType);
    }
    String name = methodDef.name;
    List<ParamDef> params = parseParams(methodDef.params);
    MethodDef def =
        new MethodDef(name, returnType, params, methodDef.body, isVirtual, classDef.name);
    String signature = SignatureUtil.signature(name, params);
    for (MethodDef existing : classDef.methods) {
      if (SignatureUtil.signature(existing.name, existing.params).equals(signature)) {
        throw new CompileError("Method already defined: " + signature);
      }
    }
    classDef.methods.add(def);
  }

  private void addConstructor(ClassDef classDef, ConstructorNode ctorDef) {
    String name = ctorDef.name;
    if (!name.equals(classDef.name)) {
      throw new CompileError("Constructor name must match class: " + name);
    }
    List<ParamDef> params = parseParams(ctorDef.params);
    String signature = SignatureUtil.signature(name, params);
    for (ConstructorDef existing : classDef.constructors) {
      if (SignatureUtil.signature(existing.className, existing.params).equals(signature)) {
        throw new CompileError("Constructor already defined: " + signature);
      }
    }
    classDef.constructors.add(new ConstructorDef(classDef.name, params, ctorDef.body));
  }

  private List<ParamDef> parseParams(List<ParamNode> paramNodes) {
    List<ParamDef> params = new ArrayList<>();
    if (paramNodes == null) {
      return params;
    }
    Set<String> names = new HashSet<>();
    for (ParamNode paramNode : paramNodes) {
      Type type = parseType(paramNode.type);
      if (type.isVoid()) {
        throw new CompileError("Parameter type cannot be void");
      }
      String name = paramNode.name;
      if (names.contains(name)) {
        throw new CompileError("Duplicate parameter: " + name);
      }
      names.add(name);
      params.add(new ParamDef(type, name));
    }
    return params;
  }

  private Type parseType(TypeNode typeNode) {
    String base = typeNode.name;
    boolean isRef = typeNode.isRef;
    if ("int".equals(base)) {
      return Type.intType(isRef);
    }
    if ("bool".equals(base)) {
      return Type.boolType(isRef);
    }
    if ("char".equals(base)) {
      return Type.charType(isRef);
    }
    if ("string".equals(base)) {
      return Type.stringType(isRef);
    }
    if ("void".equals(base)) {
      if (isRef) {
        throw new CompileError("void& is not allowed");
      }
      return Type.voidType();
    }
    if (!program.classes.containsKey(base)) {
      throw new CompileError("Unknown class type: " + base);
    }
    return Type.classType(base, isRef);
  }

  private void ensureUniqueFunction(FunctionDef def) {
    List<FunctionDef> existing = program.functions.get(def.name);
    if (existing == null) {
      return;
    }
    String signature = SignatureUtil.signature(def.name, def.params);
    for (FunctionDef other : existing) {
      if (SignatureUtil.signature(other.name, other.params).equals(signature)) {
        throw new CompileError("Function already defined: " + signature);
      }
    }
  }

  private void resolveBaseClasses() {
    for (ClassDef classDef : program.classes.values()) {
      if (classDef.baseName != null) {
        ClassDef base = program.classes.get(classDef.baseName);
        if (base == null) {
          throw new CompileError("Unknown base class: " + classDef.baseName);
        }
        classDef.baseClass = base;
      }
    }
    detectInheritanceCycles();
  }

  private void validateFieldNames() {
    for (ClassDef classDef : program.classes.values()) {
      Set<String> seen = new HashSet<>();
      ClassDef current = classDef;
      while (current != null) {
        for (FieldDef field : current.fields) {
          if (seen.contains(field.name)) {
            throw new CompileError("Duplicate field in inheritance chain: " + field.name);
          }
          seen.add(field.name);
        }
        current = current.baseClass;
      }
    }
  }

  private void detectInheritanceCycles() {
    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();
    for (ClassDef classDef : program.classes.values()) {
      detectCycle(classDef, visiting, visited);
    }
  }

  private void detectCycle(ClassDef classDef, Set<String> visiting, Set<String> visited) {
    if (visited.contains(classDef.name)) {
      return;
    }
    if (visiting.contains(classDef.name)) {
      throw new CompileError("Inheritance cycle at: " + classDef.name);
    }
    visiting.add(classDef.name);
    if (classDef.baseClass != null) {
      detectCycle(classDef.baseClass, visiting, visited);
    }
    visiting.remove(classDef.name);
    visited.add(classDef.name);
  }

  private void buildVtables() {
    for (ClassDef classDef : program.classes.values()) {
      buildVtable(classDef, new HashMap<>());
    }
  }

  private void buildVtable(ClassDef classDef, Map<String, MethodDef> inherited) {
    if (!classDef.vtable.isEmpty()) {
      return;
    }
    if (classDef.baseClass != null) {
      buildVtable(classDef.baseClass, inherited);
      inherited = new HashMap<>(classDef.baseClass.vtable);
    } else {
      inherited = new HashMap<>();
    }

    Map<String, MethodDef> table = new HashMap<>(inherited);
    for (MethodDef method : classDef.methods) {
      String signature = SignatureUtil.signature(method.name, method.params);
      table.put(signature, method);
    }
    classDef.vtable.putAll(table);
  }

  private void ensureDefaultConstructors() {
    for (ClassDef classDef : program.classes.values()) {
      boolean hasDefault = false;
      for (ConstructorDef ctor : classDef.constructors) {
        if (ctor.params.isEmpty()) {
          hasDefault = true;
          break;
        }
      }
      if (!hasDefault) {
        classDef.constructors.add(new ConstructorDef(classDef.name, List.of(), null));
      }
    }
  }
}
