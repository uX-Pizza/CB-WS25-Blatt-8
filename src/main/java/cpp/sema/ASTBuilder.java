package cpp.sema;

import cpp.antlr.cppBaseVisitor;
import cpp.antlr.cppParser;
import cpp.ast.ASTNode;
import cpp.ast.BlockNode;
import cpp.ast.ExprNode;
import cpp.ast.FunctionNode;
import cpp.ast.ParamNode;
import cpp.ast.ProgramNode;
import cpp.ast.StmtNode;
import cpp.ast.TypeNode;
import cpp.ast.VarDeclNode;

import java.util.ArrayList;
import java.util.List;

public class ASTBuilder extends cppBaseVisitor<ASTNode> {

    public Scope currentScope = new Scope(null);

    @Override
    public ASTNode visitProgram(cppParser.ProgramContext ctx) {
        ProgramNode program = new ProgramNode();
        for (var decl : ctx.topLevelDecl()) {
            program.declarations.add((ASTNode) visit(decl));
        }
        return program;
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
}
