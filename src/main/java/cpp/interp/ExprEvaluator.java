package cpp.interp;

import cpp.antlr.cppParser;
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

  public EvalResult evalExpr(cppParser.ExprContext ctx, ExecContext context) {
    return evalAssignment(ctx.assignment(), context);
  }

  private EvalResult evalAssignment(cppParser.AssignmentContext ctx, ExecContext context) {
    EvalResult left = evalLogicalOr(ctx.logicalOr(), context);
    if (ctx.assignment() == null) {
      return left;
    }
    if (!left.isLValue) {
      throw new CompileError("Assignment target is not an lvalue");
    }
    EvalResult right = evalAssignment(ctx.assignment(), context);
    Type targetType = left.slot.getDeclaredType().withoutRef();
    objectModel.assignValueToSlot(left.slot, targetType, right);
    return new EvalResult(right.value, right.type, false, null, false);
  }

  private EvalResult evalLogicalOr(cppParser.LogicalOrContext ctx, ExecContext context) {
    EvalResult left = evalLogicalAnd(ctx.logicalAnd(0), context);
    for (int i = 1; i < ctx.logicalAnd().size(); i++) {
      if (left.type.kind != Type.Kind.BOOL) {
        throw new CompileError("|| requires bool operands");
      }
      boolean leftVal = (boolean) left.value.data;
      if (leftVal) {
        return new EvalResult(Value.boolValue(true), Type.boolType(false), false, null, false);
      }
      EvalResult right = evalLogicalAnd(ctx.logicalAnd(i), context);
      if (right.type.kind != Type.Kind.BOOL) {
        throw new CompileError("|| requires bool operands");
      }
      left =
          new EvalResult(
              Value.boolValue((boolean) right.value.data), Type.boolType(false), false, null, false);
    }
    return left;
  }

  private EvalResult evalLogicalAnd(cppParser.LogicalAndContext ctx, ExecContext context) {
    EvalResult left = evalEquality(ctx.equality(0), context);
    for (int i = 1; i < ctx.equality().size(); i++) {
      if (left.type.kind != Type.Kind.BOOL) {
        throw new CompileError("&& requires bool operands");
      }
      boolean leftVal = (boolean) left.value.data;
      if (!leftVal) {
        return new EvalResult(Value.boolValue(false), Type.boolType(false), false, null, false);
      }
      EvalResult right = evalEquality(ctx.equality(i), context);
      if (right.type.kind != Type.Kind.BOOL) {
        throw new CompileError("&& requires bool operands");
      }
      left =
          new EvalResult(
              Value.boolValue((boolean) right.value.data), Type.boolType(false), false, null, false);
    }
    return left;
  }

  private EvalResult evalEquality(cppParser.EqualityContext ctx, ExecContext context) {
    EvalResult left = evalRelational(ctx.relational(0), context);
    for (int i = 1; i < ctx.relational().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      EvalResult right = evalRelational(ctx.relational(i), context);
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
      left = new EvalResult(Value.boolValue(result), Type.boolType(false), false, null, false);
    }
    return left;
  }

  private EvalResult evalRelational(cppParser.RelationalContext ctx, ExecContext context) {
    EvalResult left = evalAdditive(ctx.additive(0), context);
    for (int i = 1; i < ctx.additive().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      EvalResult right = evalAdditive(ctx.additive(i), context);
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
      left = new EvalResult(Value.boolValue(result), Type.boolType(false), false, null, false);
    }
    return left;
  }

  private EvalResult evalAdditive(cppParser.AdditiveContext ctx, ExecContext context) {
    EvalResult left = evalMultiplicative(ctx.multiplicative(0), context);
    for (int i = 1; i < ctx.multiplicative().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      EvalResult right = evalMultiplicative(ctx.multiplicative(i), context);
      objectModel.expectType(left.type, right.type, "arithmetic");
      if (left.type.kind != Type.Kind.INT) {
        throw new CompileError("Arithmetic requires int operands");
      }
      int l = (int) left.value.data;
      int r = (int) right.value.data;
      int result = op.equals("+") ? l + r : l - r;
      left = new EvalResult(Value.intValue(result), Type.intType(false), false, null, false);
    }
    return left;
  }

  private EvalResult evalMultiplicative(cppParser.MultiplicativeContext ctx, ExecContext context) {
    EvalResult left = evalUnary(ctx.unary(0), context);
    for (int i = 1; i < ctx.unary().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      EvalResult right = evalUnary(ctx.unary(i), context);
      objectModel.expectType(left.type, right.type, "arithmetic");
      if (left.type.kind != Type.Kind.INT) {
        throw new CompileError("Arithmetic requires int operands");
      }
      int l = (int) left.value.data;
      int r = (int) right.value.data;
      int result;
      switch (op) {
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
      left = new EvalResult(Value.intValue(result), Type.intType(false), false, null, false);
    }
    return left;
  }

  private EvalResult evalUnary(cppParser.UnaryContext ctx, ExecContext context) {
    if (ctx.unary() != null) {
      String op = ctx.getChild(0).getText();
      EvalResult value = evalUnary(ctx.unary(), context);
      if (op.equals("!")) {
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
    return evalPostfix(ctx.postfix(), context);
  }

  private EvalResult evalPostfix(cppParser.PostfixContext ctx, ExecContext context) {
    EvalResult current = evalPrimary(ctx.primary(), context);
    int i = 1;
    while (i < ctx.getChildCount()) {
      String dot = ctx.getChild(i).getText();
      if (!".".equals(dot)) {
        throw new CompileError("Invalid postfix expression");
      }
      String member = ctx.getChild(i + 1).getText();
      i += 2;
      if (i < ctx.getChildCount() && "(".equals(ctx.getChild(i).getText())) {
        cppParser.ArgListContext argCtx = null;
        if (i + 1 < ctx.getChildCount() && ctx.getChild(i + 1) instanceof cppParser.ArgListContext) {
          argCtx = (cppParser.ArgListContext) ctx.getChild(i + 1);
          i++;
        }
        i++;
        if (i < ctx.getChildCount() && ")".equals(ctx.getChild(i).getText())) {
          i++;
        }
        List<ArgInfo> args = evalArgs(argCtx, context);
        current = invokeMethod(current, member, args, context);
      } else {
        current = accessField(current, member);
      }
    }
    return current;
  }

  private EvalResult evalPrimary(cppParser.PrimaryContext ctx, ExecContext context) {
    if (ctx.literal() != null) {
      return evalLiteral(ctx.literal());
    }
    if (ctx.ID() != null && ctx.getChildCount() == 1) {
      String name = ctx.ID().getText();
      VarSlot slot = resolveVarSlot(name, context);
      if (slot != null) {
        Value value = slot.get();
        Type type = slot.getDeclaredType().withoutRef();
        boolean isRefBinding = slot.isRef();
        return new EvalResult(value, type, true, slot, isRefBinding);
      }
      throw new CompileError("Unknown identifier: " + name);
    }
    if (ctx.ID() != null && ctx.getChildCount() > 1) {
      String name = ctx.ID().getText();
      List<ArgInfo> args = evalArgs(ctx.argList(), context);
      if (program.classes.containsKey(name)) {
        Instance instance = objectModel.createInstance(program.classes.get(name), args);
        Type type = Type.classType(name, false);
        return new EvalResult(new Value(type, instance), type, false, null, false);
      }
      return invokeFunction(name, args, context);
    }
    if (ctx.expr() != null) {
      return evalExpr(ctx.expr(), context);
    }
    throw new CompileError("Unknown primary expression");
  }

  private EvalResult evalLiteral(cppParser.LiteralContext ctx) {
    if (ctx.INT() != null) {
      int value = Integer.parseInt(ctx.INT().getText());
      return new EvalResult(Value.intValue(value), Type.intType(false), false, null, false);
    }
    if (ctx.BOOL() != null) {
      boolean value = ctx.BOOL().getText().equals("true");
      return new EvalResult(Value.boolValue(value), Type.boolType(false), false, null, false);
    }
    if (ctx.CHAR() != null) {
      char value = parseCharLiteral(ctx.CHAR().getText());
      return new EvalResult(Value.charValue(value), Type.charType(false), false, null, false);
    }
    if (ctx.STRING() != null) {
      String value = parseStringLiteral(ctx.STRING().getText());
      return new EvalResult(Value.stringValue(value), Type.stringType(false), false, null, false);
    }
    throw new CompileError("Unknown literal");
  }

  private char parseCharLiteral(String text) {
    String body = text.substring(1, text.length() - 1);
    if (body.startsWith("\\")) {
      return parseEscape(body.charAt(1));
    }
    if (body.length() != 1) {
      throw new CompileError("Invalid char literal");
    }
    return body.charAt(0);
  }

  private String parseStringLiteral(String text) {
    String body = text.substring(1, text.length() - 1);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < body.length(); i++) {
      char c = body.charAt(i);
      if (c == '\\') {
        if (i + 1 >= body.length()) {
          throw new CompileError("Invalid string escape");
        }
        out.append(parseEscape(body.charAt(i + 1)));
        i++;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private char parseEscape(char c) {
    return switch (c) {
      case 'n' -> '\n';
      case 't' -> '\t';
      case 'r' -> '\r';
      case '0' -> '\0';
      case '\\' -> '\\';
      case '\'' -> '\'';
      case '"' -> '"';
      default -> c;
    };
  }

  private List<ArgInfo> evalArgs(cppParser.ArgListContext ctx, ExecContext context) {
    List<ArgInfo> args = new ArrayList<>();
    if (ctx == null) {
      return args;
    }
    for (cppParser.ExprContext expr : ctx.expr()) {
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
      stmtExecutor.executeBlock((cppParser.BlockContext) selected.body, fnContext, false);
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
        new ExecContext(new cpp.runtime.Env(null), instance, program.classes.get(target.declaredIn));
    stmtExecutor.bindParams(methodContext.env, target.params, args, methodContext.currentClass);
    try {
      stmtExecutor.executeBlock((cppParser.BlockContext) target.body, methodContext, false);
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
