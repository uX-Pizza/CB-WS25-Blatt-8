package cpp.interp;

import cpp.antlr.cppLexer;
import cpp.antlr.cppParser;
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
      org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
      cppParser parser = new cppParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(new ParserErrorListener());
      cppParser.ReplInputContext repl = parser.replInput();
      if (repl.topLevelDecl() != null) {
        cppParser.TopLevelDeclContext decl = repl.topLevelDecl();
        if (decl.classDef() != null) {
          registerReplClass(decl.classDef());
        } else if (decl.functionDef() != null) {
          registerReplFunction(decl.functionDef());
        }
      } else if (repl.stmt() != null) {
        ExecContext context = new ExecContext(sessionEnv, null, null);
        cppParser.StmtContext stmt = repl.stmt();
        if (stmt.exprStmt() != null) {
          EvalResult result = exprEvaluator.evalExpr(stmt.exprStmt().expr(), context);
          if (!result.type.isVoid()) {
            builtins.printValue(result.value);
          }
        } else {
          stmtExecutor.executeStmt(stmt, context);
        }
      } else if (repl.expr() != null) {
        ExecContext context = new ExecContext(sessionEnv, null, null);
        EvalResult result = exprEvaluator.evalExpr(repl.expr(), context);
        builtins.printValue(result.value);
      }
    } catch (CompileError | RuntimeError ex) {
      IO.println("Error: " + ex.getMessage());
    }
  }

  private void registerReplClass(cppParser.ClassDefContext ctx) {
    String name = ctx.ID(0).getText();
    if (program.classes.containsKey(name)) {
      throw new CompileError("Class already defined: " + name);
    }
    String baseName = null;
    if (ctx.ID().size() > 1) {
      baseName = ctx.ID(1).getText();
      if (!program.classes.containsKey(baseName)) {
        throw new CompileError("Unknown base class: " + baseName);
      }
    }
    ClassDef classDef = new ClassDef(name, baseName);
    if (baseName != null) {
      classDef.baseClass = program.classes.get(baseName);
    }
    program.classes.put(name, classDef);

    for (cppParser.ClassMemberContext member : ctx.classMember()) {
      if (member.fieldDecl() != null) {
        addFieldRepl(classDef, member.fieldDecl());
      } else if (member.methodDef() != null) {
        addMethodRepl(classDef, member.methodDef());
      } else if (member.constructorDef() != null) {
        addConstructorRepl(classDef, member.constructorDef());
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

  private void registerReplFunction(cppParser.FunctionDefContext ctx) {
    Type returnType = typeResolver.parse(ctx.type());
    if (returnType.isRef) {
      throw new CompileError("Reference return types are not allowed: " + returnType);
    }
    String name = ctx.ID().getText();
    List<ParamDef> params = parseParams(ctx.paramList());
    FunctionDef def = new FunctionDef(name, returnType, params, ctx.block());
    dispatch.ensureUniqueFunction(def);
    program.addFunction(def);
  }

  private void addFieldRepl(ClassDef classDef, cppParser.FieldDeclContext ctx) {
    Type type = typeResolver.parse(ctx.type());
    if (type.isRef) {
      throw new CompileError("Reference fields are not allowed: " + type);
    }
    if (type.isVoid()) {
      throw new CompileError("Field type cannot be void");
    }
    String name = ctx.ID().getText();
    Set<String> existing = objectModel.collectFieldNames(classDef);
    if (existing.contains(name)) {
      throw new CompileError("Field already defined: " + name);
    }
    classDef.fields.add(new FieldDef(type, name));
  }

  private void addMethodRepl(ClassDef classDef, cppParser.MethodDefContext ctx) {
    boolean isVirtual = ctx.getChild(0).getText().equals("virtual");
    Type returnType = typeResolver.parse(ctx.type());
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

  private void addConstructorRepl(ClassDef classDef, cppParser.ConstructorDefContext ctx) {
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
      Type type = typeResolver.parse(paramCtx.type());
      if (type.isVoid()) {
        throw new CompileError("Parameter type cannot be void");
      }
      String name = paramCtx.ID().getText();
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
