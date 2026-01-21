package cpp.interp;

import cpp.ast.BlockNode;
import cpp.ast.ExprStmtNode;
import cpp.ast.IfStmtNode;
import cpp.ast.ReturnStmtNode;
import cpp.ast.StmtNode;
import cpp.ast.VarDeclNode;
import cpp.ast.WhileStmtNode;
import cpp.error.CompileError;
import cpp.model.ClassDef;
import cpp.model.Type;
import cpp.runtime.Env;
import cpp.runtime.EvalResult;
import cpp.runtime.ReturnSignal;
import cpp.sema.TypeResolver;
import java.util.Set;

public class StmtExecutor {
  private final TypeResolver typeResolver;
  private final Dispatch dispatch;
  private final ObjectModel objectModel;
  private ExprEvaluator exprEvaluator;

  public StmtExecutor(TypeResolver typeResolver, Dispatch dispatch, ObjectModel objectModel) {
    this.typeResolver = typeResolver;
    this.dispatch = dispatch;
    this.objectModel = objectModel;
  }

  public void setExprEvaluator(ExprEvaluator exprEvaluator) {
    this.exprEvaluator = exprEvaluator;
  }

  public void executeBlock(BlockNode block, ExecContext context, boolean createScope) {
    Env env = context.env;
    if (createScope) {
      env = new Env(env);
    }
    ExecContext local = new ExecContext(env, context.instance, context.currentClass);
    for (StmtNode stmt : block.statements) {
      executeStmt(stmt, local);
    }
  }

  public void executeStmt(StmtNode stmt, ExecContext context) {
    if (stmt instanceof VarDeclNode varDecl) {
      executeVarDecl(varDecl, context);
      return;
    }
    if (stmt instanceof ExprStmtNode exprStmt) {
      exprEvaluator.evalExpr(exprStmt.expr, context);
      return;
    }
    if (stmt instanceof IfStmtNode ifStmt) {
      executeIf(ifStmt, context);
      return;
    }
    if (stmt instanceof WhileStmtNode whileStmt) {
      executeWhile(whileStmt, context);
      return;
    }
    if (stmt instanceof ReturnStmtNode returnStmt) {
      executeReturn(returnStmt, context);
      return;
    }
    if (stmt instanceof BlockNode block) {
      executeBlock(block, context, true);
      return;
    }
    throw new CompileError("Unknown statement");
  }

  public void executeVarDecl(VarDeclNode decl, ExecContext context) {
    Type type = typeResolver.parse(decl.type);
    String name = decl.name;
    if (type.isVoid()) {
      throw new CompileError("Variable type cannot be void");
    }
    if (context.env.containsInCurrentScope(name)) {
      throw new CompileError("Variable already defined in scope: " + name);
    }
    if (context.currentClass != null) {
      Set<String> fieldNames = objectModel.collectFieldNames(context.currentClass);
      if (fieldNames.contains(name)) {
        throw new CompileError("Variable shadows field: " + name);
      }
    }
    if (type.isRef && decl.init == null) {
      throw new CompileError("Reference variable requires initializer: " + name);
    }
    if (decl.init == null) {
      context.env.define(name, objectModel.createValueSlot(type, objectModel.defaultValue(type)));
      return;
    }
    EvalResult init = exprEvaluator.evalExpr(decl.init, context);
    if (type.isRef) {
      if (!init.isLValue) {
        throw new CompileError("Reference initializer must be lvalue: " + name);
      }
      Type target = type.withoutRef();
      if (target.isClass() && init.type.isClass()) {
        if (!objectModel.isDerivedFrom(init.type.className, target.className)) {
          throw new CompileError(
              "Type mismatch in reference init: expected " + target + " got " + init.type);
        }
      } else {
        objectModel.expectType(target, init.type, "reference init");
      }
      context.env.define(name, objectModel.createRefSlot(type, init.slot));
      return;
    }
    context.env.define(name, objectModel.createValueSlot(type, init.value));
  }

  private void executeIf(IfStmtNode stmt, ExecContext context) {
    boolean cond = evalCondition(stmt.condition, context);
    if (cond) {
      executeBlock(stmt.thenBlock, context, true);
    } else if (stmt.elseBlock != null) {
      executeBlock(stmt.elseBlock, context, true);
    }
  }

  private void executeWhile(WhileStmtNode stmt, ExecContext context) {
    while (evalCondition(stmt.condition, context)) {
      executeBlock(stmt.body, context, true);
    }
  }

  private void executeReturn(ReturnStmtNode stmt, ExecContext context) {
    if (stmt.value == null) {
      throw new ReturnSignal(cpp.runtime.Value.voidValue());
    }
    EvalResult result = exprEvaluator.evalExpr(stmt.value, context);
    throw new ReturnSignal(result.value);
  }

  private boolean evalCondition(cpp.ast.ExprNode expr, ExecContext context) {
    EvalResult result = exprEvaluator.evalExpr(expr, context);
    if (result.type.kind == Type.Kind.BOOL) {
      return (boolean) result.value.data;
    }
    if (result.type.kind == Type.Kind.INT) {
      return ((int) result.value.data) != 0;
    }
    if (result.type.kind == Type.Kind.CHAR) {
      return ((char) result.value.data) != 0;
    }
    if (result.type.kind == Type.Kind.STRING) {
      return !((String) result.value.data).isEmpty();
    }
    throw new CompileError("Invalid condition type: " + result.type);
  }

  public void bindParams(
      Env env,
      java.util.List<cpp.model.ParamDef> params,
      java.util.List<ArgInfo> args,
      ClassDef currentClass) {
    dispatch.bindParams(env, params, args, currentClass);
  }

  public TypeResolver getTypeResolver() {
    return typeResolver;
  }
}
