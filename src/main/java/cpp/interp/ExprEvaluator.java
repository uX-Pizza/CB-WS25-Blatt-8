package cpp.interp;

import cpp.ast.AssignExprNode;
import cpp.ast.BinaryExprNode;
import cpp.ast.CallExprNode;
import cpp.ast.ExprNode;
import cpp.ast.FieldAccessNode;
import cpp.ast.LiteralNode;
import cpp.ast.MethodCallNode;
import cpp.ast.UnaryExprNode;
import cpp.ast.VarRefNode;
import cpp.error.CompileError;
import cpp.error.RuntimeError;
import cpp.model.ClassDef;
import cpp.model.FunctionDef;
import cpp.model.MethodDef;
import cpp.model.ProgramDef;
import cpp.model.Type;
import cpp.runtime.EvalResult;
import cpp.runtime.Instance;
import cpp.runtime.ReturnSignal;
import cpp.runtime.Value;
import cpp.runtime.VarSlot;
import cpp.sema.SignatureUtil;
import cpp.sema.TypeResolver;
import java.util.ArrayList;
import java.util.List;

public class ExprEvaluator {
  private final ProgramDef program;
  private final TypeResolver typeResolver;
  private final Dispatch dispatch;
  private final ObjectModel objectModel;
  private final Builtins builtins;
  private StmtExecutor stmtExecutor;

  public ExprEvaluator(
      ProgramDef program,
      TypeResolver typeResolver,
      Dispatch dispatch,
      ObjectModel objectModel,
      Builtins builtins) {
    this.program = program;
    this.typeResolver = typeResolver;
    this.dispatch = dispatch;
    this.objectModel = objectModel;
    this.builtins = builtins;
  }

  public void setStmtExecutor(StmtExecutor stmtExecutor) {
    this.stmtExecutor = stmtExecutor;
  }

  public EvalResult evalExpr(ExprNode expr, ExecContext context) {
    if (expr instanceof AssignExprNode assignExpr) {
      return evalAssignment(assignExpr, context);
    }
    if (expr instanceof BinaryExprNode binaryExpr) {
      return evalBinary(binaryExpr, context);
    }
    if (expr instanceof UnaryExprNode unaryExpr) {
      return evalUnary(unaryExpr, context);
    }
    if (expr instanceof LiteralNode literal) {
      return evalLiteral(literal);
    }
    if (expr instanceof VarRefNode varRef) {
      return evalVarRef(varRef, context);
    }
    if (expr instanceof CallExprNode call) {
      return evalCall(call, context);
    }
    if (expr instanceof MethodCallNode methodCall) {
      return evalMethodCall(methodCall, context);
    }
    if (expr instanceof FieldAccessNode fieldAccess) {
      return evalFieldAccess(fieldAccess, context);
    }
    throw new CompileError("Unknown expression");
  }

  private EvalResult evalAssignment(AssignExprNode expr, ExecContext context) {
    EvalResult left = evalExpr(expr.target, context);
    if (!left.isLValue) {
      throw new CompileError("Assignment target is not an lvalue");
    }
    EvalResult right = evalExpr(expr.value, context);
    Type targetType = left.slot.getDeclaredType().withoutRef();
    objectModel.assignValueToSlot(left.slot, targetType, right);
    return new EvalResult(right.value, right.type, false, null, false);
  }

  private EvalResult evalBinary(BinaryExprNode expr, ExecContext context) {
    String op = expr.op;
    if ("||".equals(op)) {
      EvalResult left = evalExpr(expr.left, context);
      if (left.type.kind != Type.Kind.BOOL) {
        throw new CompileError("|| requires bool operands");
      }
      boolean leftVal = (boolean) left.value.data;
      if (leftVal) {
        return new EvalResult(Value.boolValue(true), Type.boolType(false), false, null, false);
      }
      EvalResult right = evalExpr(expr.right, context);
      if (right.type.kind != Type.Kind.BOOL) {
        throw new CompileError("|| requires bool operands");
      }
      return new EvalResult(
          Value.boolValue((boolean) right.value.data),
          Type.boolType(false),
          false,
          null,
          false);
    }
    if ("&&".equals(op)) {
      EvalResult left = evalExpr(expr.left, context);
      if (left.type.kind != Type.Kind.BOOL) {
        throw new CompileError("&& requires bool operands");
      }
      boolean leftVal = (boolean) left.value.data;
      if (!leftVal) {
        return new EvalResult(Value.boolValue(false), Type.boolType(false), false, null, false);
      }
      EvalResult right = evalExpr(expr.right, context);
      if (right.type.kind != Type.Kind.BOOL) {
        throw new CompileError("&& requires bool operands");
      }
      return new EvalResult(
          Value.boolValue((boolean) right.value.data),
          Type.boolType(false),
          false,
          null,
          false);
    }

    EvalResult left = evalExpr(expr.left, context);
    EvalResult right = evalExpr(expr.right, context);

    if ("==".equals(op) || "!=".equals(op)) {
      objectModel.expectType(left.type, right.type, "comparison");
      boolean result;
      if (left.type.kind == Type.Kind.INT) {
        int l = (int) left.value.data;
        int r = (int) right.value.data;
        result = op.equals("==") ? l == r : l != r;
      } else if (left.type.kind == Type.Kind.CHAR) {
        char l = (char) left.value.data;
        char r = (char) right.value.data;
        result = op.equals("==") ? l == r : l != r;
      } else if (left.type.kind == Type.Kind.BOOL) {
        boolean l = (boolean) left.value.data;
        boolean r = (boolean) right.value.data;
        result = op.equals("==") ? l == r : l != r;
      } else if (left.type.kind == Type.Kind.STRING) {
        String l = (String) left.value.data;
        String r = (String) right.value.data;
        result = op.equals("==") ? l.equals(r) : !l.equals(r);
      } else {
        throw new CompileError("Unsupported == for type: " + left.type);
      }
      return new EvalResult(Value.boolValue(result), Type.boolType(false), false, null, false);
    }

    if ("<".equals(op) || "<=".equals(op) || ">".equals(op) || ">=".equals(op)) {
      objectModel.expectType(left.type, right.type, "comparison");
      boolean result;
      if (left.type.kind == Type.Kind.INT) {
        int l = (int) left.value.data;
        int r = (int) right.value.data;
        result =
            switch (op) {
              case "<" -> l < r;
              case "<=" -> l <= r;
              case ">" -> l > r;
              case ">=" -> l >= r;
              default -> throw new CompileError("Unknown operator: " + op);
            };
      } else if (left.type.kind == Type.Kind.CHAR) {
        char l = (char) left.value.data;
        char r = (char) right.value.data;
        result =
            switch (op) {
              case "<" -> l < r;
              case "<=" -> l <= r;
              case ">" -> l > r;
              case ">=" -> l >= r;
              default -> throw new CompileError("Unknown operator: " + op);
            };
      } else {
        throw new CompileError("Relational operators require int or char");
      }
      return new EvalResult(Value.boolValue(result), Type.boolType(false), false, null, false);
    }

    objectModel.expectType(left.type, right.type, "arithmetic");
    if (left.type.kind != Type.Kind.INT) {
      throw new CompileError("Arithmetic requires int operands");
    }
    int l = (int) left.value.data;
    int r = (int) right.value.data;
    int result;
    switch (op) {
      case "+" -> result = l + r;
      case "-" -> result = l - r;
      case "*" -> result = l * r;
      case "/" -> {
        if (r == 0) {
          throw new RuntimeError("Division by zero");
        }
        result = l / r;
      }
      case "%" -> {
        if (r == 0) {
          throw new RuntimeError("Division by zero");
        }
        result = l % r;
      }
      default -> throw new CompileError("Unknown operator: " + op);
    }
    return new EvalResult(Value.intValue(result), Type.intType(false), false, null, false);
  }

  private EvalResult evalUnary(UnaryExprNode expr, ExecContext context) {
    String op = expr.op;
    EvalResult value = evalExpr(expr.expr, context);
    if ("!".equals(op)) {
      if (value.type.kind != Type.Kind.BOOL) {
        throw new CompileError("! requires bool operand");
      }
      return new EvalResult(
          Value.boolValue(!(boolean) value.value.data), Type.boolType(false), false, null, false);
    }
    if (value.type.kind != Type.Kind.INT) {
      throw new CompileError("Unary +/- requires int operand");
    }
    int v = (int) value.value.data;
    int result = op.equals("-") ? -v : v;
    return new EvalResult(Value.intValue(result), Type.intType(false), false, null, false);
  }

  private EvalResult evalVarRef(VarRefNode expr, ExecContext context) {
    VarSlot slot = resolveVarSlot(expr.name, context);
    if (slot != null) {
      Value value = slot.get();
      Type type = slot.getDeclaredType().withoutRef();
      boolean isRefBinding = slot.isRef();
      return new EvalResult(value, type, true, slot, isRefBinding);
    }
    throw new CompileError("Unknown identifier: " + expr.name);
  }

  private EvalResult evalCall(CallExprNode call, ExecContext context) {
    List<ArgInfo> args = evalArgs(call.args, context);
    if (program.classes.containsKey(call.name)) {
      Instance instance = objectModel.createInstance(program.classes.get(call.name), args);
      Type type = Type.classType(call.name, false);
      return new EvalResult(new Value(type, instance), type, false, null, false);
    }
    return invokeFunction(call.name, args, context);
  }

  private EvalResult evalMethodCall(MethodCallNode call, ExecContext context) {
    EvalResult receiver = evalExpr(call.receiver, context);
    List<ArgInfo> args = evalArgs(call.args, context);
    return invokeMethod(receiver, call.name, args, context);
  }

  private EvalResult evalFieldAccess(FieldAccessNode access, ExecContext context) {
    EvalResult receiver = evalExpr(access.receiver, context);
    return accessField(receiver, access.name);
  }

  private EvalResult evalLiteral(LiteralNode literal) {
    Object value = literal.value;
    if (value instanceof Integer intValue) {
      return new EvalResult(Value.intValue(intValue), Type.intType(false), false, null, false);
    }
    if (value instanceof Boolean boolValue) {
      return new EvalResult(Value.boolValue(boolValue), Type.boolType(false), false, null, false);
    }
    if (value instanceof Character charValue) {
      return new EvalResult(Value.charValue(charValue), Type.charType(false), false, null, false);
    }
    if (value instanceof String stringValue) {
      return new EvalResult(
          Value.stringValue(stringValue), Type.stringType(false), false, null, false);
    }
    throw new CompileError("Unknown literal");
  }

  private List<ArgInfo> evalArgs(List<ExprNode> exprs, ExecContext context) {
    List<ArgInfo> args = new ArrayList<>();
    if (exprs == null) {
      return args;
    }
    for (ExprNode expr : exprs) {
      EvalResult result = evalExpr(expr, context);
      args.add(new ArgInfo(result));
    }
    return args;
  }

  private EvalResult invokeFunction(String name, List<ArgInfo> args, ExecContext context) {
    List<FunctionDef> candidates = program.functions.get(name);
    if (candidates == null) {
      throw new CompileError("Unknown function: " + name);
    }
    FunctionDef selected = dispatch.selectFunction(candidates, args);
    if (selected.isBuiltin) {
      builtins.execute(name, args);
      return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
    }
    ExecContext fnContext = new ExecContext(new cpp.runtime.Env(null), null, null);
    stmtExecutor.bindParams(fnContext.env, selected.params, args, null);
    try {
      stmtExecutor.executeBlock(selected.body, fnContext, false);
    } catch (ReturnSignal signal) {
      if (selected.returnType.isVoid()) {
        if (signal.value != null && !signal.value.type.isVoid()) {
          throw new CompileError("Return with value in void function");
        }
        return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
      }
      objectModel.expectType(selected.returnType, signal.value.type, "return");
      return new EvalResult(signal.value, selected.returnType, false, null, false);
    }
    if (!selected.returnType.isVoid()) {
      throw new RuntimeError("Missing return in function: " + selected.name);
    }
    return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
  }

  private EvalResult invokeMethod(
      EvalResult receiver, String name, List<ArgInfo> args, ExecContext context) {
    if (!receiver.type.isClass()) {
      throw new CompileError("Member access on non-class type");
    }
    ClassDef staticClass = program.classes.get(receiver.type.className);
    MethodDef selected = dispatch.selectMethod(staticClass, name, args);
    String signature = SignatureUtil.signature(selected.name, selected.params);
    boolean isVirtual = dispatch.isVirtualInStatic(staticClass, signature);
    MethodDef target = selected;
    Instance instance = (Instance) receiver.value.data;
    if (isVirtual && receiver.isRefBinding) {
      MethodDef impl = instance.classDef.vtable.get(signature);
      if (impl != null) {
        target = impl;
      }
    }
    ExecContext methodContext =
        new ExecContext(
            new cpp.runtime.Env(null), instance, program.classes.get(target.declaredIn));
    stmtExecutor.bindParams(methodContext.env, target.params, args, methodContext.currentClass);
    try {
      stmtExecutor.executeBlock(target.body, methodContext, false);
    } catch (ReturnSignal signal) {
      if (target.returnType.isVoid()) {
        if (signal.value != null && !signal.value.type.isVoid()) {
          throw new CompileError("Return with value in void method");
        }
        return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
      }
      objectModel.expectType(target.returnType, signal.value.type, "return");
      return new EvalResult(signal.value, target.returnType, false, null, false);
    }
    if (!target.returnType.isVoid()) {
      throw new RuntimeError("Missing return in method: " + target.name);
    }
    return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
  }

  private EvalResult accessField(EvalResult receiver, String name) {
    if (!receiver.type.isClass()) {
      throw new CompileError("Field access on non-class type");
    }
    ClassDef staticClass = program.classes.get(receiver.type.className);
    if (!objectModel.hasField(staticClass, name)) {
      throw new CompileError("Unknown field: " + name);
    }
    Instance instance = (Instance) receiver.value.data;
    VarSlot slot = instance.fields.get(name);
    if (slot == null) {
      throw new CompileError("Unknown field: " + name);
    }
    Type type = slot.getDeclaredType().withoutRef();
    return new EvalResult(slot.get(), type, true, slot, false);
  }

  private VarSlot resolveVarSlot(String name, ExecContext context) {
    VarSlot slot = context.env.resolve(name);
    if (slot != null) {
      return slot;
    }
    if (context.instance != null) {
      VarSlot field = context.instance.fields.get(name);
      if (field != null) {
        return field;
      }
    }
    return null;
  }
}
