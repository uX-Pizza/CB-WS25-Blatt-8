package cpp.interp;

import cpp.antlr.cppParser;
import cpp.error.CompileError;
import cpp.model.FunctionDef;
import cpp.model.ProgramDef;
import cpp.model.Type;
import cpp.runtime.Env;
import cpp.runtime.ReturnSignal;
import cpp.sema.TypeResolver;
import cpp.util.IO;

public class Interpreter {
  private final ProgramDef program;
  private final TypeResolver typeResolver;
  private final Builtins builtins;
  private final ObjectModel objectModel;
  private final Dispatch dispatch;
  private final ExprEvaluator exprEvaluator;
  private final StmtExecutor stmtExecutor;
  private final ReplHandler replHandler;

  public Interpreter(ProgramDef program) {
    this.program = program;
    this.typeResolver = new TypeResolver(program);
    this.builtins = new Builtins();
    this.objectModel = new ObjectModel(program);
    this.dispatch = new Dispatch(program, objectModel);
    this.stmtExecutor = new StmtExecutor(typeResolver, dispatch, objectModel);
    this.exprEvaluator =
        new ExprEvaluator(program, typeResolver, dispatch, objectModel, builtins);
    this.replHandler =
        new ReplHandler(program, typeResolver, dispatch, objectModel, stmtExecutor, exprEvaluator, builtins);

    stmtExecutor.setExprEvaluator(exprEvaluator);
    exprEvaluator.setStmtExecutor(stmtExecutor);
    objectModel.setStmtExecutor(stmtExecutor);

    builtins.register(program);
  }

  public Env runMain() {
    FunctionDef main = dispatch.resolveMain();
    if (main == null) {
      return new Env(null);
    }
    Env sessionEnv = new Env(null);
    ExecContext context = new ExecContext(sessionEnv, null, null);
    try {
      stmtExecutor.executeBlock((cppParser.BlockContext) main.body, context, false);
    } catch (ReturnSignal signal) {
      if (!main.returnType.isVoid()) {
        objectModel.expectType(main.returnType, signal.value.type, "return");
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
      replHandler.processInput(input);
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

  public void setReplSessionEnv(Env env) {
    replHandler.setSessionEnv(env);
  }
}
