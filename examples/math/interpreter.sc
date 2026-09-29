import "ast.sc"::*;
import "lexer.sc"::*;
import except::panic;
import extensions;
import list::*;
import math;

warp float run(Expr expr) {
    return when(expr) {
        Int value -> value.value;
        Float value -> value.value;
        Variable var -> findVar(var.name);

        Binary bi -> bi.calculate();
        Call c -> c.calculate();
    };
}

private warp float Expr.Binary.calculate() {
    auto left = run(this.left);
    auto right = run(this.right);

    return when(this.operation) {
        MathOperation.ADD -> left + right;
        MathOperation.SUB -> left - right;
        MathOperation.MUL -> left * right;
        MathOperation.DIV -> left / right;
        MathOperation.MOD -> left % right;
        MathOperation.POW -> math::pow(left, right);
    };
}

private warp float Expr.Call.calculate() {
    return when(this.name) {
        "sqrt" -> {
            if(this.arguments.length() != 1) panic("Sqrt expects 1 argument!");

            math::sqrt(run(this.arguments[0]));
        }
        else -> {
            panic("No such function: ${this.name}");
            0;
        }
    };
}

private warp float findVar(str name) {
    return when(name) {
        "Pi" -> 3.14;
        "e" -> 2.71;

        else -> {
            panic("No such var ${name}");
            0;
        }
    };
}