import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Interpreter {
  private final ProgramDef program;
  private final TypeResolver typeResolver;

  public Interpreter(ProgramDef program) {
    this.program = program;
    this.typeResolver = new TypeResolver(program);
    registerBuiltins();
  }

  public Env runMain() {
    FunctionDef main = resolveMain();
    if (main == null) {
      return new Env(null);
    }
    Env sessionEnv = new Env(null);
    ExecContext context = new ExecContext(sessionEnv, null, null);
    try {
      executeBlock((cppParser.BlockContext) main.body, context, false);
    } catch (ReturnSignal signal) {
      if (!main.returnType.isVoid()) {
        expectType(main.returnType, signal.value.type, "return");
      }
    }
    return sessionEnv;
  }

  public void executeRepl() {
    StringBuilder buffer = new StringBuilder();
    int balance = 0;
    while (true) {
      String prompt = balance == 0 ? "cpp> " : "...> ";
      String line = IO.readln(prompt);
      if (line == null) {
        break;
      }
      if (buffer.length() == 0 && line.trim().equals(".exit")) {
        break;
      }
      buffer.append(line).append("\n");
      balance += countBalanceDelta(line);
      if (balance > 0) {
        continue;
      }
      String input = buffer.toString();
      buffer.setLength(0);
      processReplInput(input);
    }
  }

  private int countBalanceDelta(String line) {
    int delta = 0;
    for (char c : line.toCharArray()) {
      if (c == '{' || c == '(') {
        delta++;
      } else if (c == '}' || c == ')') {
        delta--;
      }
    }
    return delta;
  }

  private void processReplInput(String input) {
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
        ExecContext context = new ExecContext(replSessionEnv, null, null);
        cppParser.StmtContext stmt = repl.stmt();
        if (stmt.exprStmt() != null) {
          EvalResult result = evalExpr(stmt.exprStmt().expr(), context);
          if (!result.type.isVoid()) {
            printValue(result.value);
          }
        } else {
          executeStmt(stmt, context);
        }
      } else if (repl.expr() != null) {
        ExecContext context = new ExecContext(replSessionEnv, null, null);
        EvalResult result = evalExpr(repl.expr(), context);
        printValue(result.value);
      }
    } catch (CompileError | RuntimeError ex) {
      IO.println("Error: " + ex.getMessage());
    }
  }

  private Env replSessionEnv = null;

  public void setReplSessionEnv(Env env) {
    this.replSessionEnv = env;
  }

  private FunctionDef resolveMain() {
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

  private void registerBuiltins() {
    addBuiltin("print_bool", Type.voidType(), List.of(new ParamDef(Type.boolType(false), "v")));
    addBuiltin("print_int", Type.voidType(), List.of(new ParamDef(Type.intType(false), "v")));
    addBuiltin("print_char", Type.voidType(), List.of(new ParamDef(Type.charType(false), "v")));
    addBuiltin(
        "print_string", Type.voidType(), List.of(new ParamDef(Type.stringType(false), "v")));
  }

  private void addBuiltin(String name, Type returnType, List<ParamDef> params) {
    FunctionDef def = new FunctionDef(name, returnType, params, null, true);
    program.addFunction(def);
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
    ensureUniqueFunction(def);
    program.addFunction(def);
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

  private void addFieldRepl(ClassDef classDef, cppParser.FieldDeclContext ctx) {
    Type type = typeResolver.parse(ctx.type());
    if (type.isRef) {
      throw new CompileError("Reference fields are not allowed: " + type);
    }
    if (type.isVoid()) {
      throw new CompileError("Field type cannot be void");
    }
    String name = ctx.ID().getText();
    Set<String> existing = collectFieldNames(classDef);
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

  private void executeBlock(cppParser.BlockContext ctx, ExecContext context, boolean createScope) {
    Env env = context.env;
    if (createScope) {
      env = new Env(env);
    }
    ExecContext local = new ExecContext(env, context.instance, context.currentClass);
    for (cppParser.StmtContext stmt : ctx.stmt()) {
      executeStmt(stmt, local);
    }
  }

  private void executeStmt(cppParser.StmtContext ctx, ExecContext context) {
    if (ctx.varDecl() != null) {
      executeVarDecl(ctx.varDecl(), context);
      return;
    }
    if (ctx.exprStmt() != null) {
      evalExpr(ctx.exprStmt().expr(), context);
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

  private void executeVarDecl(cppParser.VarDeclContext ctx, ExecContext context) {
    Type type = typeResolver.parse(ctx.type());
    String name = ctx.ID().getText();
    if (type.isVoid()) {
      throw new CompileError("Variable type cannot be void");
    }
    if (context.env.containsInCurrentScope(name)) {
      throw new CompileError("Variable already defined in scope: " + name);
    }
    if (context.currentClass != null) {
      Set<String> fieldNames = collectFieldNames(context.currentClass);
      if (fieldNames.contains(name)) {
        throw new CompileError("Variable shadows field: " + name);
      }
    }
    if (type.isRef && ctx.expr() == null) {
      throw new CompileError("Reference variable requires initializer: " + name);
    }
    if (ctx.expr() == null) {
      Value defaultValue = defaultValue(type);
      context.env.define(name, new VarSlot(type, defaultValue));
      return;
    }
    EvalResult init = evalExpr(ctx.expr(), context);
    if (type.isRef) {
      if (!init.isLValue) {
        throw new CompileError("Reference initializer must be lvalue: " + name);
      }
      Type target = type.withoutRef();
      if (target.isClass() && init.type.isClass()) {
        if (!isDerivedFrom(init.type.className, target.className)) {
          throw new CompileError(
              "Type mismatch in reference init: expected " + target + " got " + init.type);
        }
      } else {
        expectType(target, init.type, "reference init");
      }
      context.env.define(name, VarSlot.refSlot(type, init.slot));
      return;
    }
    Value value = coerceValue(init.value, type);
    context.env.define(name, new VarSlot(type, value));
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
      throw new ReturnSignal(Value.voidValue());
    }
    EvalResult result = evalExpr(ctx.expr(), context);
    throw new ReturnSignal(result.value);
  }

  private boolean evalCondition(cppParser.ExprContext ctx, ExecContext context) {
    EvalResult result = evalExpr(ctx, context);
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

  private EvalResult evalExpr(cppParser.ExprContext ctx, ExecContext context) {
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
    assignValueToSlot(left.slot, targetType, right);
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
      left = new EvalResult(Value.boolValue((boolean) right.value.data), Type.boolType(false), false, null, false);
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
      left = new EvalResult(Value.boolValue((boolean) right.value.data), Type.boolType(false), false, null, false);
    }
    return left;
  }

  private EvalResult evalEquality(cppParser.EqualityContext ctx, ExecContext context) {
    EvalResult left = evalRelational(ctx.relational(0), context);
    for (int i = 1; i < ctx.relational().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      EvalResult right = evalRelational(ctx.relational(i), context);
      expectType(left.type, right.type, "comparison");
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
      expectType(left.type, right.type, "comparison");
      boolean result;
      if (left.type.kind == Type.Kind.INT) {
        int l = (int) left.value.data;
        int r = (int) right.value.data;
        result = switch (op) {
          case "<" -> l < r;
          case "<=" -> l <= r;
          case ">" -> l > r;
          case ">=" -> l >= r;
          default -> throw new CompileError("Unknown operator: " + op);
        };
      } else if (left.type.kind == Type.Kind.CHAR) {
        char l = (char) left.value.data;
        char r = (char) right.value.data;
        result = switch (op) {
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
      expectType(left.type, right.type, "arithmetic");
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
      expectType(left.type, right.type, "arithmetic");
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
        return new EvalResult(Value.boolValue(!(boolean) value.value.data), Type.boolType(false), false, null, false);
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
        Instance instance = createInstance(program.classes.get(name), args);
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
    FunctionDef selected = selectFunction(candidates, args);
    if (selected.isBuiltin) {
      executeBuiltin(name, args);
      return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
    }
    Env env = new Env(null);
    bindParams(env, selected.params, args, null);
    ExecContext fnContext = new ExecContext(env, null, null);
    try {
      executeBlock((cppParser.BlockContext) selected.body, fnContext, false);
    } catch (ReturnSignal signal) {
      if (selected.returnType.isVoid()) {
        if (signal.value != null && !signal.value.type.isVoid()) {
          throw new CompileError("Return with value in void function");
        }
        return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
      }
      expectType(selected.returnType, signal.value.type, "return");
      return new EvalResult(signal.value, selected.returnType, false, null, false);
    }
    if (!selected.returnType.isVoid()) {
      throw new RuntimeError("Missing return in function: " + selected.name);
    }
    return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
  }

  private EvalResult invokeMethod(EvalResult receiver, String name, List<ArgInfo> args, ExecContext context) {
    if (!receiver.type.isClass()) {
      throw new CompileError("Member access on non-class type");
    }
    ClassDef staticClass = program.classes.get(receiver.type.className);
    MethodDef selected = selectMethod(staticClass, name, args);
    String signature = SignatureUtil.signature(selected.name, selected.params);
    boolean isVirtual = isVirtualInStatic(staticClass, signature);
    MethodDef target = selected;
    Instance instance = (Instance) receiver.value.data;
    if (isVirtual && receiver.isRefBinding) {
      MethodDef impl = instance.classDef.vtable.get(signature);
      if (impl != null) {
        target = impl;
      }
    }
    Env env = new Env(null);
    ClassDef declaredClass = program.classes.get(target.declaredIn);
    bindParams(env, target.params, args, declaredClass);
    ExecContext methodContext = new ExecContext(env, instance, declaredClass);
    try {
      executeBlock((cppParser.BlockContext) target.body, methodContext, false);
    } catch (ReturnSignal signal) {
      if (target.returnType.isVoid()) {
        if (signal.value != null && !signal.value.type.isVoid()) {
          throw new CompileError("Return with value in void method");
        }
        return new EvalResult(Value.voidValue(), Type.voidType(), false, null, false);
      }
      expectType(target.returnType, signal.value.type, "return");
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
    if (!hasField(staticClass, name)) {
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

  private boolean hasField(ClassDef classDef, String name) {
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

  private MethodDef selectMethod(ClassDef staticClass, String name, List<ArgInfo> args) {
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

  private boolean isVirtualInStatic(ClassDef staticClass, String signature) {
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

  private FunctionDef selectFunction(List<FunctionDef> candidates, List<ArgInfo> args) {
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

  private void bindParams(Env env, List<ParamDef> params, List<ArgInfo> args, ClassDef currentClass) {
    for (int i = 0; i < params.size(); i++) {
      ParamDef param = params.get(i);
      ArgInfo arg = args.get(i);
      if (currentClass != null) {
        if (collectFieldNames(currentClass).contains(param.name)) {
          throw new CompileError("Parameter shadows field: " + param.name);
        }
      }
      if (param.type.isRef) {
        env.define(param.name, VarSlot.refSlot(param.type, arg.result.slot));
      } else {
        Value value = coerceValue(arg.result.value, param.type);
        env.define(param.name, new VarSlot(param.type, value));
      }
    }
  }

  private Instance createInstance(ClassDef classDef, List<ArgInfo> args) {
    ConstructorDef ctor = selectConstructor(classDef, args);
    Instance instance = new Instance(classDef);
    initializeFields(instance, classDef);
    if (classDef.baseClass != null) {
      callBaseDefaultConstructor(instance, classDef.baseClass);
    }
    if (ctor.body != null) {
      Env env = new Env(null);
      bindParams(env, ctor.params, args, classDef);
      ExecContext ctorContext = new ExecContext(env, instance, classDef);
      try {
        executeBlock((cppParser.BlockContext) ctor.body, ctorContext, false);
      } catch (ReturnSignal signal) {
        throw new CompileError("Return not allowed in constructor");
      }
    } else if (ctor.isSyntheticCopy) {
      Instance source = (Instance) args.get(0).result.value.data;
      copyInto(instance, source, classDef.name);
    }
    return instance;
  }

  private void callBaseDefaultConstructor(Instance instance, ClassDef baseClass) {
    if (baseClass.baseClass != null) {
      callBaseDefaultConstructor(instance, baseClass.baseClass);
    }
    ConstructorDef baseCtor = selectConstructor(baseClass, List.of());
    if (baseCtor.body == null) {
      return;
    }
    Env env = new Env(null);
    ExecContext ctorContext = new ExecContext(env, instance, baseClass);
    try {
      executeBlock((cppParser.BlockContext) baseCtor.body, ctorContext, false);
    } catch (ReturnSignal signal) {
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

  private void initializeFields(Instance instance, ClassDef classDef) {
    if (classDef.baseClass != null) {
      initializeFields(instance, classDef.baseClass);
    }
    for (FieldDef field : classDef.fields) {
      Value value = defaultValue(field.type);
      instance.fields.put(field.name, new VarSlot(field.type, value));
    }
  }

  private Set<String> collectFieldNames(ClassDef classDef) {
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

  private Value defaultValue(Type type) {
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

  private Value coerceValue(Value value, Type targetType) {
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

  private void assignValueToSlot(VarSlot slot, Type targetType, EvalResult right) {
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

  private void expectType(Type expected, Type actual, String context) {
    if (!expected.equals(actual)) {
      throw new CompileError("Type mismatch in " + context + ": expected " + expected + " got " + actual);
    }
  }

  private boolean isDerivedFrom(String derived, String base) {
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

  private void copyInto(Instance target, Instance source, String targetClassName) {
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

  private void executeBuiltin(String name, List<ArgInfo> args) {
    Value value = args.get(0).result.value;
    switch (name) {
      case "print_bool" -> IO.println(((boolean) value.data) ? "1" : "0");
      case "print_int" -> IO.println(Integer.toString((int) value.data));
      case "print_char" -> IO.println(Character.toString((char) value.data));
      case "print_string" -> IO.println((String) value.data);
      default -> throw new CompileError("Unknown builtin: " + name);
    }
  }

  private void printValue(Value value) {
    if (value.type.kind == Type.Kind.BOOL) {
      IO.println(((boolean) value.data) ? "1" : "0");
    } else if (value.type.kind == Type.Kind.INT) {
      IO.println(Integer.toString((int) value.data));
    } else if (value.type.kind == Type.Kind.CHAR) {
      IO.println(Character.toString((char) value.data));
    } else if (value.type.kind == Type.Kind.STRING) {
      IO.println((String) value.data);
    }
  }

  private static class ArgInfo {
    public final EvalResult result;

    public ArgInfo(EvalResult result) {
      this.result = result;
    }
  }

  private static class ExecContext {
    public final Env env;
    public final Instance instance;
    public final ClassDef currentClass;

    public ExecContext(Env env, Instance instance, ClassDef currentClass) {
      this.env = env;
      this.instance = instance;
      this.currentClass = currentClass;
    }
  }
}
