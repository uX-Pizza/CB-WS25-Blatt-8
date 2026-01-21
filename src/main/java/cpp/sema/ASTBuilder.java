package cpp.sema;

import cpp.antlr.cppBaseVisitor;
import cpp.antlr.cppParser;
import cpp.ast.ASTNode;
import cpp.ast.AssignExprNode;
import cpp.ast.BinaryExprNode;
import cpp.ast.BlockNode;
import cpp.ast.CallExprNode;
import cpp.ast.ClassDefNode;
import cpp.ast.ClassMemberNode;
import cpp.ast.ConstructorNode;
import cpp.ast.ExprNode;
import cpp.ast.ExprStmtNode;
import cpp.ast.FieldAccessNode;
import cpp.ast.FieldDeclNode;
import cpp.ast.FunctionNode;
import cpp.ast.IfStmtNode;
import cpp.ast.LiteralNode;
import cpp.ast.MethodCallNode;
import cpp.ast.MethodNode;
import cpp.ast.ParamNode;
import cpp.ast.ProgramNode;
import cpp.ast.ReturnStmtNode;
import cpp.ast.StmtNode;
import cpp.ast.TypeNode;
import cpp.ast.UnaryExprNode;
import cpp.ast.VarDeclNode;
import cpp.ast.VarRefNode;
import cpp.ast.WhileStmtNode;
import java.util.ArrayList;
import java.util.List;

public class ASTBuilder extends cppBaseVisitor<ASTNode> {

  public Scope currentScope = new Scope(null);

  @Override
  public ASTNode visitProgram(cppParser.ProgramContext ctx) {
    ProgramNode program = new ProgramNode();
    for (var decl : ctx.topLevelDecl()) {
      program.declarations.add(visit(decl));
    }
    return program;
  }

  @Override
  public ASTNode visitReplInput(cppParser.ReplInputContext ctx) {
    if (ctx.topLevelDecl() != null) {
      return visit(ctx.topLevelDecl());
    }
    if (ctx.stmt() != null) {
      return visit(ctx.stmt());
    }
    if (ctx.expr() != null) {
      return visit(ctx.expr());
    }
    return super.visitReplInput(ctx);
  }

  @Override
  public ASTNode visitFunctionDef(cppParser.FunctionDefContext ctx) {
    TypeNode returnType = (TypeNode) visit(ctx.type());
    String name = ctx.ID().getText();

    List<ParamNode> params = new ArrayList<>();
    if (ctx.paramList() != null) {
      for (var p : ctx.paramList().param()) {
        if (p != null) {
          params.add((ParamNode) visit(p));
        }
      }
    }

    // Neue Scope
    Scope oldScope = currentScope;
    currentScope = new Scope(oldScope);

    // Parameter in Scope eintragen
    for (ParamNode p : params) {
      currentScope.define(new Symbol(p.name, p.type));
    }

    BlockNode body = (BlockNode) visit(ctx.block());

    currentScope = oldScope;

    return new FunctionNode(returnType, name, params, body);
  }

  @Override
  public ASTNode visitClassDef(cppParser.ClassDefContext ctx) {
    String name = ctx.ID(0).getText();
    String baseName = null;
    if (ctx.ID().size() > 1) {
      baseName = ctx.ID(1).getText();
    }
    ClassDefNode classDef = new ClassDefNode(name, baseName);
    for (cppParser.ClassMemberContext member : ctx.classMember()) {
      classDef.members.add((ClassMemberNode) visit(member));
    }
    return classDef;
  }

  @Override
  public ASTNode visitClassMember(cppParser.ClassMemberContext ctx) {
    if (ctx.fieldDecl() != null) {
      return visit(ctx.fieldDecl());
    }
    if (ctx.methodDef() != null) {
      return visit(ctx.methodDef());
    }
    if (ctx.constructorDef() != null) {
      return visit(ctx.constructorDef());
    }
    return super.visitClassMember(ctx);
  }

  @Override
  public ASTNode visitFieldDecl(cppParser.FieldDeclContext ctx) {
    TypeNode type = (TypeNode) visit(ctx.type());
    return new FieldDeclNode(type, ctx.ID().getText());
  }

  @Override
  public ASTNode visitMethodDef(cppParser.MethodDefContext ctx) {
    boolean isVirtual = ctx.getChild(0).getText().equals("virtual");
    TypeNode returnType = (TypeNode) visit(ctx.type());
    String name = ctx.ID().getText();
    List<ParamNode> params = parseParams(ctx.paramList());
    BlockNode body = (BlockNode) visit(ctx.block());
    return new MethodNode(isVirtual, returnType, name, params, body);
  }

  @Override
  public ASTNode visitConstructorDef(cppParser.ConstructorDefContext ctx) {
    String name = ctx.ID().getText();
    List<ParamNode> params = parseParams(ctx.paramList());
    BlockNode body = (BlockNode) visit(ctx.block());
    return new ConstructorNode(name, params, body);
  }

  @Override
  public ASTNode visitParam(cppParser.ParamContext ctx) {
    TypeNode type = (TypeNode) visit(ctx.type());
    return new ParamNode(type, ctx.ID().getText());
  }

  @Override
  public ASTNode visitType(cppParser.TypeContext ctx) {
    String name = ctx.baseType().getText();
    boolean isRef = ctx.ref() != null;
    return new TypeNode(name, isRef);
  }

  @Override
  public ASTNode visitVarDecl(cppParser.VarDeclContext ctx) {
    TypeNode type = (TypeNode) visit(ctx.type());
    String name = ctx.ID().getText();

    ExprNode init = null;
    if (ctx.expr() != null) {
      init = (ExprNode) visit(ctx.expr());
    }

    if (!currentScope.define(new Symbol(name, type))) {
      throw new RuntimeException("Variable doppelt definiert: " + name);
    }

    return new VarDeclNode(type, name, init);
  }

  @Override
  public ASTNode visitStmt(cppParser.StmtContext ctx) {
    if (ctx.varDecl() != null) {
      return visit(ctx.varDecl());
    }
    if (ctx.exprStmt() != null) {
      return visit(ctx.exprStmt());
    }
    if (ctx.ifStmt() != null) {
      return visit(ctx.ifStmt());
    }
    if (ctx.whileStmt() != null) {
      return visit(ctx.whileStmt());
    }
    if (ctx.returnStmt() != null) {
      return visit(ctx.returnStmt());
    }
    if (ctx.block() != null) {
      return visit(ctx.block());
    }
    return super.visitStmt(ctx);
  }

  @Override
  public ASTNode visitExprStmt(cppParser.ExprStmtContext ctx) {
    ExprNode expr = (ExprNode) visit(ctx.expr());
    return new ExprStmtNode(expr);
  }

  @Override
  public ASTNode visitIfStmt(cppParser.IfStmtContext ctx) {
    ExprNode condition = (ExprNode) visit(ctx.expr());
    BlockNode thenBlock = (BlockNode) visit(ctx.block(0));
    BlockNode elseBlock = null;
    if (ctx.block().size() > 1) {
      elseBlock = (BlockNode) visit(ctx.block(1));
    }
    return new IfStmtNode(condition, thenBlock, elseBlock);
  }

  @Override
  public ASTNode visitWhileStmt(cppParser.WhileStmtContext ctx) {
    ExprNode condition = (ExprNode) visit(ctx.expr());
    BlockNode body = (BlockNode) visit(ctx.block());
    return new WhileStmtNode(condition, body);
  }

  @Override
  public ASTNode visitReturnStmt(cppParser.ReturnStmtContext ctx) {
    ExprNode value = null;
    if (ctx.expr() != null) {
      value = (ExprNode) visit(ctx.expr());
    }
    return new ReturnStmtNode(value);
  }

  @Override
  public ASTNode visitBlock(cppParser.BlockContext ctx) {
    BlockNode block = new BlockNode();

    Scope old = currentScope;
    currentScope = new Scope(old);

    for (var stmt : ctx.stmt()) {
      block.statements.add((StmtNode) visit(stmt));
    }

    currentScope = old;
    return block;
  }

  @Override
  public ASTNode visitExpr(cppParser.ExprContext ctx) {
    return visit(ctx.assignment());
  }

  @Override
  public ASTNode visitAssignment(cppParser.AssignmentContext ctx) {
    ExprNode left = (ExprNode) visit(ctx.logicalOr());
    if (ctx.assignment() == null) {
      return left;
    }
    ExprNode right = (ExprNode) visit(ctx.assignment());
    return new AssignExprNode(left, right);
  }

  @Override
  public ASTNode visitLogicalOr(cppParser.LogicalOrContext ctx) {
    ExprNode left = (ExprNode) visit(ctx.logicalAnd(0));
    for (int i = 1; i < ctx.logicalAnd().size(); i++) {
      ExprNode right = (ExprNode) visit(ctx.logicalAnd(i));
      left = new BinaryExprNode("||", left, right);
    }
    return left;
  }

  @Override
  public ASTNode visitLogicalAnd(cppParser.LogicalAndContext ctx) {
    ExprNode left = (ExprNode) visit(ctx.equality(0));
    for (int i = 1; i < ctx.equality().size(); i++) {
      ExprNode right = (ExprNode) visit(ctx.equality(i));
      left = new BinaryExprNode("&&", left, right);
    }
    return left;
  }

  @Override
  public ASTNode visitEquality(cppParser.EqualityContext ctx) {
    ExprNode left = (ExprNode) visit(ctx.relational(0));
    for (int i = 1; i < ctx.relational().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      ExprNode right = (ExprNode) visit(ctx.relational(i));
      left = new BinaryExprNode(op, left, right);
    }
    return left;
  }

  @Override
  public ASTNode visitRelational(cppParser.RelationalContext ctx) {
    ExprNode left = (ExprNode) visit(ctx.additive(0));
    for (int i = 1; i < ctx.additive().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      ExprNode right = (ExprNode) visit(ctx.additive(i));
      left = new BinaryExprNode(op, left, right);
    }
    return left;
  }

  @Override
  public ASTNode visitAdditive(cppParser.AdditiveContext ctx) {
    ExprNode left = (ExprNode) visit(ctx.multiplicative(0));
    for (int i = 1; i < ctx.multiplicative().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      ExprNode right = (ExprNode) visit(ctx.multiplicative(i));
      left = new BinaryExprNode(op, left, right);
    }
    return left;
  }

  @Override
  public ASTNode visitMultiplicative(cppParser.MultiplicativeContext ctx) {
    ExprNode left = (ExprNode) visit(ctx.unary(0));
    for (int i = 1; i < ctx.unary().size(); i++) {
      String op = ctx.getChild(2 * i - 1).getText();
      ExprNode right = (ExprNode) visit(ctx.unary(i));
      left = new BinaryExprNode(op, left, right);
    }
    return left;
  }

  @Override
  public ASTNode visitUnary(cppParser.UnaryContext ctx) {
    if (ctx.unary() != null) {
      String op = ctx.getChild(0).getText();
      ExprNode value = (ExprNode) visit(ctx.unary());
      return new UnaryExprNode(op, value);
    }
    return visit(ctx.postfix());
  }

  @Override
  public ASTNode visitPostfix(cppParser.PostfixContext ctx) {
    ExprNode current = (ExprNode) visit(ctx.primary());
    int i = 1;
    while (i < ctx.getChildCount()) {
      String dot = ctx.getChild(i).getText();
      if (!".".equals(dot)) {
        break;
      }
      String member = ctx.getChild(i + 1).getText();
      i += 2;
      if (i < ctx.getChildCount() && "(".equals(ctx.getChild(i).getText())) {
        cppParser.ArgListContext argCtx = null;
        if (i + 1 < ctx.getChildCount()
            && ctx.getChild(i + 1) instanceof cppParser.ArgListContext) {
          argCtx = (cppParser.ArgListContext) ctx.getChild(i + 1);
          i++;
        }
        i++;
        if (i < ctx.getChildCount() && ")".equals(ctx.getChild(i).getText())) {
          i++;
        }
        List<ExprNode> args = parseArgs(argCtx);
        current = new MethodCallNode(current, member, args);
      } else {
        current = new FieldAccessNode(current, member);
      }
    }
    return current;
  }

  @Override
  public ASTNode visitPrimary(cppParser.PrimaryContext ctx) {
    if (ctx.literal() != null) {
      return visit(ctx.literal());
    }
    if (ctx.ID() != null && ctx.getChildCount() == 1) {
      return new VarRefNode(ctx.ID().getText());
    }
    if (ctx.ID() != null && ctx.getChildCount() > 1) {
      String name = ctx.ID().getText();
      List<ExprNode> args = parseArgs(ctx.argList());
      return new CallExprNode(name, args);
    }
    if (ctx.expr() != null) {
      return visit(ctx.expr());
    }
    return super.visitPrimary(ctx);
  }

  @Override
  public ASTNode visitLiteral(cppParser.LiteralContext ctx) {
    if (ctx.INT() != null) {
      return new LiteralNode(Integer.parseInt(ctx.INT().getText()));
    }
    if (ctx.BOOL() != null) {
      return new LiteralNode(ctx.BOOL().getText().equals("true"));
    }
    if (ctx.CHAR() != null) {
      return new LiteralNode(parseCharLiteral(ctx.CHAR().getText()));
    }
    if (ctx.STRING() != null) {
      return new LiteralNode(parseStringLiteral(ctx.STRING().getText()));
    }
    return super.visitLiteral(ctx);
  }

  private List<ParamNode> parseParams(cppParser.ParamListContext ctx) {
    List<ParamNode> params = new ArrayList<>();
    if (ctx == null) {
      return params;
    }
    for (cppParser.ParamContext paramCtx : ctx.param()) {
      params.add((ParamNode) visit(paramCtx));
    }
    return params;
  }

  private List<ExprNode> parseArgs(cppParser.ArgListContext ctx) {
    List<ExprNode> args = new ArrayList<>();
    if (ctx == null) {
      return args;
    }
    for (cppParser.ExprContext exprCtx : ctx.expr()) {
      args.add((ExprNode) visit(exprCtx));
    }
    return args;
  }

  private char parseCharLiteral(String text) {
    String body = text.substring(1, text.length() - 1);
    if (body.startsWith("\\")) {
      return parseEscape(body.charAt(1));
    }
    if (body.length() != 1) {
      throw new RuntimeException("Invalid char literal");
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
          throw new RuntimeException("Invalid string escape");
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
}
