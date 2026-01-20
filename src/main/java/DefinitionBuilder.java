import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefinitionBuilder {
  private final ProgramDef program = new ProgramDef();

  public ProgramDef build(cppParser.ProgramContext ctx) {
    for (cppParser.TopLevelDeclContext decl : ctx.topLevelDecl()) {
      if (decl.classDef() != null) {
        registerClassHeader(decl.classDef());
      }
    }

    for (cppParser.TopLevelDeclContext decl : ctx.topLevelDecl()) {
      if (decl.functionDef() != null) {
        registerFunction(decl.functionDef());
      }
    }

    for (cppParser.TopLevelDeclContext decl : ctx.topLevelDecl()) {
      if (decl.classDef() != null) {
        populateClassMembers(decl.classDef());
      }
    }

    resolveBaseClasses();
    validateFieldNames();
    buildVtables();
    ensureDefaultConstructors();

    return program;
  }

  private void registerClassHeader(cppParser.ClassDefContext ctx) {
    String name = ctx.ID(0).getText();
    String baseName = null;
    if (ctx.ID().size() > 1) {
      baseName = ctx.ID(1).getText();
    }
    if (program.classes.containsKey(name)) {
      throw new CompileError("Class already defined: " + name);
    }
    program.classes.put(name, new ClassDef(name, baseName));
  }

  private void registerFunction(cppParser.FunctionDefContext ctx) {
    Type returnType = parseType(ctx.type());
    if (returnType.isRef) {
      throw new CompileError("Reference return types are not allowed: " + returnType);
    }
    String name = ctx.ID().getText();
    List<ParamDef> params = parseParams(ctx.paramList());
    FunctionDef def = new FunctionDef(name, returnType, params, ctx.block());
    ensureUniqueFunction(def);
    program.addFunction(def);
  }

  private void populateClassMembers(cppParser.ClassDefContext ctx) {
    String name = ctx.ID(0).getText();
    ClassDef classDef = program.classes.get(name);
    for (cppParser.ClassMemberContext member : ctx.classMember()) {
      if (member.fieldDecl() != null) {
        addField(classDef, member.fieldDecl());
      } else if (member.methodDef() != null) {
        addMethod(classDef, member.methodDef());
      } else if (member.constructorDef() != null) {
        addConstructor(classDef, member.constructorDef());
      }
    }
  }

  private void addField(ClassDef classDef, cppParser.FieldDeclContext ctx) {
    Type type = parseType(ctx.type());
    if (type.isRef) {
      throw new CompileError("Reference fields are not allowed: " + type);
    }
    if (type.isVoid()) {
      throw new CompileError("Field type cannot be void");
    }
    String name = ctx.ID().getText();
    for (FieldDef field : classDef.fields) {
      if (field.name.equals(name)) {
        throw new CompileError("Field already defined: " + name);
      }
    }
    classDef.fields.add(new FieldDef(type, name));
  }

  private void addMethod(ClassDef classDef, cppParser.MethodDefContext ctx) {
    boolean isVirtual = ctx.getChild(0).getText().equals("virtual");
    Type returnType = parseType(ctx.type());
    if (returnType.isRef) {
      throw new CompileError("Reference return types are not allowed: " + returnType);
    }
    String name = ctx.ID().getText();
    List<ParamDef> params = parseParams(ctx.paramList());
    MethodDef def =
        new MethodDef(name, returnType, params, ctx.block(), isVirtual, classDef.name);
    String signature = SignatureUtil.signature(name, params);
    for (MethodDef existing : classDef.methods) {
      if (SignatureUtil.signature(existing.name, existing.params).equals(signature)) {
        throw new CompileError("Method already defined: " + signature);
      }
    }
    classDef.methods.add(def);
  }

  private void addConstructor(ClassDef classDef, cppParser.ConstructorDefContext ctx) {
    String name = ctx.ID().getText();
    if (!name.equals(classDef.name)) {
      throw new CompileError("Constructor name must match class: " + name);
    }
    List<ParamDef> params = parseParams(ctx.paramList());
    String signature = SignatureUtil.signature(name, params);
    for (ConstructorDef existing : classDef.constructors) {
      if (SignatureUtil.signature(existing.className, existing.params).equals(signature)) {
        throw new CompileError("Constructor already defined: " + signature);
      }
    }
    classDef.constructors.add(new ConstructorDef(classDef.name, params, ctx.block()));
  }

  private List<ParamDef> parseParams(cppParser.ParamListContext ctx) {
    List<ParamDef> params = new ArrayList<>();
    if (ctx == null) {
      return params;
    }
    Set<String> names = new HashSet<>();
    for (cppParser.ParamContext paramCtx : ctx.param()) {
      Type type = parseType(paramCtx.type());
      if (type.isVoid()) {
        throw new CompileError("Parameter type cannot be void");
      }
      String name = paramCtx.ID().getText();
      if (names.contains(name)) {
        throw new CompileError("Duplicate parameter: " + name);
      }
      names.add(name);
      params.add(new ParamDef(type, name));
    }
    return params;
  }

  private Type parseType(cppParser.TypeContext ctx) {
    String base = ctx.baseType().getText();
    boolean isRef = ctx.ref() != null;
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
