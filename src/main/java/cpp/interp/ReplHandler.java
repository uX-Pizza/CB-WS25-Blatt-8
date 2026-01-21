package cpp.interp;

import cpp.antlr.cppLexer;
import cpp.antlr.cppParser;
import cpp.ast.ASTNode;
import cpp.ast.BlockNode;
import cpp.ast.ClassDefNode;
import cpp.ast.ClassMemberNode;
import cpp.ast.ConstructorNode;
import cpp.ast.ExprNode;
import cpp.ast.ExprStmtNode;
import cpp.ast.FieldDeclNode;
import cpp.ast.FunctionNode;
import cpp.ast.MethodNode;
import cpp.ast.ParamNode;
import cpp.ast.StmtNode;
import cpp.error.CompileError;
import cpp.error.RuntimeError;
import cpp.model.ClassDef;
import cpp.model.ConstructorDef;
import cpp.model.FieldDef;
import cpp.model.FunctionDef;
import cpp.model.MethodDef;
import cpp.model.ParamDef;
import cpp.model.ProgramDef;
import cpp.model.Type;
import cpp.runtime.Env;
import cpp.runtime.EvalResult;
import cpp.sema.ASTBuilder;
import cpp.sema.SignatureUtil;
import cpp.sema.TypeResolver;
import cpp.util.IO;
import cpp.util.ParserErrorListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReplHandler {
  private final ProgramDef program;
  private final TypeResolver typeResolver;
  private final Dispatch dispatch;
  private final ObjectModel objectModel;
  private final StmtExecutor stmtExecutor;
  private final ExprEvaluator exprEvaluator;
  private final Builtins builtins;
  private Env sessionEnv;

  public ReplHandler(
      ProgramDef program,
      TypeResolver typeResolver,
      Dispatch dispatch,
      ObjectModel objectModel,
      StmtExecutor stmtExecutor,
      ExprEvaluator exprEvaluator,
      Builtins builtins) {
    this.program = program;
    this.typeResolver = typeResolver;
    this.dispatch = dispatch;
    this.objectModel = objectModel;
    this.stmtExecutor = stmtExecutor;
    this.exprEvaluator = exprEvaluator;
    this.builtins = builtins;
  }

  public void setSessionEnv(Env env) {
    this.sessionEnv = env;
  }

  public void processInput(String input) {
    try {
      cppLexer lexer = new cppLexer(org.antlr.v4.runtime.CharStreams.fromString(input));
      org.antlr.v4.runtime.CommonTokenStream tokens =
          new org.antlr.v4.runtime.CommonTokenStream(lexer);
      cppParser parser = new cppParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(new ParserErrorListener());
      cppParser.ReplInputContext repl = parser.replInput();
      ASTBuilder astBuilder = new ASTBuilder();
      ASTNode node = astBuilder.visit(repl);
      if (node instanceof ClassDefNode classDefNode) {
        registerReplClass(classDefNode);
      } else if (node instanceof FunctionNode functionNode) {
        registerReplFunction(functionNode);
      } else if (node instanceof StmtNode stmt) {
        ExecContext context = new ExecContext(sessionEnv, null, null);
        if (stmt instanceof ExprStmtNode exprStmt) {
          EvalResult result = exprEvaluator.evalExpr(exprStmt.expr, context);
          if (!result.type.isVoid()) {
            builtins.printValue(result.value);
          }
        } else {
          stmtExecutor.executeStmt(stmt, context);
        }
      } else if (node instanceof ExprNode expr) {
        ExecContext context = new ExecContext(sessionEnv, null, null);
        EvalResult result = exprEvaluator.evalExpr(expr, context);
        builtins.printValue(result.value);
      }
    } catch (CompileError | RuntimeError ex) {
      IO.println("Error: " + ex.getMessage());
    }
  }

  private void registerReplClass(ClassDefNode classDefNode) {
    String name = classDefNode.name;
    if (program.classes.containsKey(name)) {
      throw new CompileError("Class already defined: " + name);
    }
    String baseName = classDefNode.baseName;
    if (baseName != null && !program.classes.containsKey(baseName)) {
      throw new CompileError("Unknown base class: " + baseName);
    }
    ClassDef classDef = new ClassDef(name, baseName);
    if (baseName != null) {
      classDef.baseClass = program.classes.get(baseName);
    }
    program.classes.put(name, classDef);

    for (ClassMemberNode member : classDefNode.members) {
      if (member instanceof FieldDeclNode fieldDecl) {
        addFieldRepl(classDef, fieldDecl);
      } else if (member instanceof MethodNode methodNode) {
        addMethodRepl(classDef, methodNode);
      } else if (member instanceof ConstructorNode ctorNode) {
        addConstructorRepl(classDef, ctorNode);
      }
    }
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
    buildVtableFor(classDef);
  }

  private void registerReplFunction(FunctionNode functionNode) {
    Type returnType = typeResolver.parse(functionNode.returnType);
    if (returnType.isRef) {
      throw new CompileError("Reference return types are not allowed: " + returnType);
    }
    String name = functionNode.name;
    List<ParamDef> params = parseParams(functionNode.params);
    BlockNode body = functionNode.body;
    FunctionDef def = new FunctionDef(name, returnType, params, body);
    dispatch.ensureUniqueFunction(def);
    program.addFunction(def);
  }

  private void addFieldRepl(ClassDef classDef, FieldDeclNode fieldDecl) {
    Type type = typeResolver.parse(fieldDecl.type);
    if (type.isRef) {
      throw new CompileError("Reference fields are not allowed: " + type);
    }
    if (type.isVoid()) {
      throw new CompileError("Field type cannot be void");
    }
    String name = fieldDecl.name;
    Set<String> existing = objectModel.collectFieldNames(classDef);
    if (existing.contains(name)) {
      throw new CompileError("Field already defined: " + name);
    }
    classDef.fields.add(new FieldDef(type, name));
  }

  private void addMethodRepl(ClassDef classDef, MethodNode methodNode) {
    boolean isVirtual = methodNode.isVirtual;
    Type returnType = typeResolver.parse(methodNode.returnType);
    if (returnType.isRef) {
      throw new CompileError("Reference return types are not allowed: " + returnType);
    }
    String name = methodNode.name;
    List<ParamDef> params = parseParams(methodNode.params);
    MethodDef def =
        new MethodDef(name, returnType, params, methodNode.body, isVirtual, classDef.name);
    String signature = SignatureUtil.signature(name, params);
    for (MethodDef existing : classDef.methods) {
      if (SignatureUtil.signature(existing.name, existing.params).equals(signature)) {
        throw new CompileError("Method already defined: " + signature);
      }
    }
    classDef.methods.add(def);
  }

  private void addConstructorRepl(ClassDef classDef, ConstructorNode ctorNode) {
    String name = ctorNode.name;
    if (!name.equals(classDef.name)) {
      throw new CompileError("Constructor name must match class: " + name);
    }
    List<ParamDef> params = parseParams(ctorNode.params);
    String signature = SignatureUtil.signature(name, params);
    for (ConstructorDef existing : classDef.constructors) {
      if (SignatureUtil.signature(existing.className, existing.params).equals(signature)) {
        throw new CompileError("Constructor already defined: " + signature);
      }
    }
    classDef.constructors.add(new ConstructorDef(classDef.name, params, ctorNode.body));
  }

  private List<ParamDef> parseParams(List<ParamNode> paramNodes) {
    List<ParamDef> params = new ArrayList<>();
    if (paramNodes == null) {
      return params;
    }
    Set<String> names = new HashSet<>();
    for (ParamNode paramNode : paramNodes) {
      Type type = typeResolver.parse(paramNode.type);
      if (type.isVoid()) {
        throw new CompileError("Parameter type cannot be void");
      }
      String name = paramNode.name;
      if (!names.add(name)) {
        throw new CompileError("Duplicate parameter: " + name);
      }
      params.add(new ParamDef(type, name));
    }
    return params;
  }

  private void buildVtableFor(ClassDef classDef) {
    Map<String, MethodDef> inherited = new HashMap<>();
    if (classDef.baseClass != null) {
      inherited.putAll(classDef.baseClass.vtable);
    }
    for (MethodDef method : classDef.methods) {
      String signature = SignatureUtil.signature(method.name, method.params);
      inherited.put(signature, method);
    }
    classDef.vtable.clear();
    classDef.vtable.putAll(inherited);
  }
}
