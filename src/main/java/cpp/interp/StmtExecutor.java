package cpp.interp;

import cpp.antlr.cppParser;
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

  public void executeBlock(cppParser.BlockContext ctx, ExecContext context, boolean createScope) {
    Env env = context.env;
    if (createScope) {
      env = new Env(env);
    }
    ExecContext local = new ExecContext(env, context.instance, context.currentClass);
    for (cppParser.StmtContext stmt : ctx.stmt()) {
      executeStmt(stmt, local);
    }
  }

  public void executeStmt(cppParser.StmtContext ctx, ExecContext context) {
    if (ctx.varDecl() != null) {
      executeVarDecl(ctx.varDecl(), context);
      return;
    }
    if (ctx.exprStmt() != null) {
      exprEvaluator.evalExpr(ctx.exprStmt().expr(), context);
      return;
    }
    if (ctx.ifStmt() != null) {
      executeIf(ctx.ifStmt(), context);
      return;
    }
    if (ctx.whileStmt() != null) {
      executeWhile(ctx.whileStmt(), context);
      return;
    }
    if (ctx.returnStmt() != null) {
      executeReturn(ctx.returnStmt(), context);
      return;
    }
    if (ctx.block() != null) {
      executeBlock(ctx.block(), context, true);
      return;
    }
    throw new CompileError("Unknown statement");
  }

  public void executeVarDecl(cppParser.VarDeclContext ctx, ExecContext context) {
    Type type = typeResolver.parse(ctx.type());
    String name = ctx.ID().getText();
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
    if (type.isRef && ctx.expr() == null) {
      throw new CompileError("Reference variable requires initializer: " + name);
    }
    if (ctx.expr() == null) {
      context.env.define(name, objectModel.createValueSlot(type, objectModel.defaultValue(type)));
      return;
    }
    EvalResult init = exprEvaluator.evalExpr(ctx.expr(), context);
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

  private void executeIf(cppParser.IfStmtContext ctx, ExecContext context) {
    boolean cond = evalCondition(ctx.expr(), context);
    if (cond) {
      executeBlock(ctx.block(0), context, true);
    } else if (ctx.block().size() > 1) {
      executeBlock(ctx.block(1), context, true);
    }
  }

  private void executeWhile(cppParser.WhileStmtContext ctx, ExecContext context) {
    while (evalCondition(ctx.expr(), context)) {
      executeBlock(ctx.block(), context, true);
    }
  }

  private void executeReturn(cppParser.ReturnStmtContext ctx, ExecContext context) {
    if (ctx.expr() == null) {
      throw new ReturnSignal(cpp.runtime.Value.voidValue());
    }
    EvalResult result = exprEvaluator.evalExpr(ctx.expr(), context);
    throw new ReturnSignal(result.value);
  }

  private boolean evalCondition(cppParser.ExprContext ctx, ExecContext context) {
    EvalResult result = exprEvaluator.evalExpr(ctx, context);
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
