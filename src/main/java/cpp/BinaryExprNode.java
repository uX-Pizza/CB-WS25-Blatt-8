package cpp;

public class BinaryExprNode extends ExprNode {
    public String op;
    public ExprNode left, right;

    public BinaryExprNode(String op, ExprNode left, ExprNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }
}
