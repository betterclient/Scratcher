import except::panic;
import "lexer.sc"::*;
import list::*;

sealed enum Expr {
    Int(int value),
    Float(float value),
    Variable(str name),

    Binary(
        Expr left,
        MathOperation operation,
        Expr right
    ),

    Call(
        str name,
        List<Expr> arguments
    )
}

warp Expr? List<LexerToken>.parse() {
    return null if this.length() == 1;

    auto parser = ASTParser(this, 0);

    auto expr = parser.expression();
    parser.expect(LexerToken.EOF);
    return expr;
}

struct ASTParser(List<LexerToken> tokens, int index);

private warp Expr ASTParser.expression() {
    return binary(
        [MathOperation.ADD, MathOperation.SUB].toList(),
        &term
    );
}

private warp Expr ASTParser.term() {
    return binary(
        [MathOperation.MUL, MathOperation.DIV, MathOperation.MOD].toList(),
        &power
    );
}

private warp Expr ASTParser.power() {
    auto left = primary();

    if (current() is LexerToken.Operation && (current() as LexerToken.Operation).op == MathOperation.POW) {
        advance();

        return Expr.Binary(
            left,
            MathOperation.POW,
            power()
        );
    }

    return left;
}

private warp Expr ASTParser.primary() {
    auto token = current();
    advance();

    return when(token) {
        LexerToken.Int t -> Expr.Int(t.value);
        LexerToken.Float t -> Expr.Float(t.value);
        LexerToken.Identifier t -> {
            if (match(LexerToken.LeftParen)) {
                call(t.text)
            } else {
                Expr.Variable(t.text)
            };
        }
        LexerToken.LeftParen -> {
            auto expr = expression();
            expect(LexerToken.RightParen);
            expr;
        }
        else -> {
            panic("Expected expression.");
            null;
        }
    }!!;
}

private warp Expr ASTParser.call(str name) {
    List<Expr> arguments = newList();

    if (!match(LexerToken.RightParen)) {
        arguments.add(expression());
        while (match(LexerToken.Comma)) {
            arguments.add(expression());
        }

        expect(LexerToken.RightParen);
    }

    return Expr.Call(name, arguments);
}

//helpers
private warp LexerToken ASTParser.current() {
    return this.tokens[this.index];
}

private warp LexerToken ASTParser.advance() {
    this.index++;
    return current();
}

private warp bool ASTParser.match(LexerToken token) {
    auto cur = current();
    bool ok = when(cur) {
        LexerToken.EOF -> token is LexerToken.EOF
        LexerToken.LeftParen -> token is LexerToken.LeftParen
        LexerToken.RightParen -> token is LexerToken.RightParen
        LexerToken.Comma -> token is LexerToken.Comma
        else -> false
    };

    if(ok) {
        this.index++;
        return true;
    }

    return false;
}

private warp void ASTParser.expect(LexerToken token) {
    if(!match(token)) {
        panic("Expected different token."); //well uhh we can't really convert the token to a string...
    }
}

private warp Expr ASTParser.binary(
    List<MathOperation> ops,
    (ASTParser) -> Expr next
) {
    auto left = next(this);

    while(
        current() is LexerToken.Operation &&
        ops.contains((current() as LexerToken.Operation).op)
    ) {
        auto op = (current() as LexerToken.Operation).op;
        advance();
        auto right = next(this);

        left = Expr.Binary(left, op, right);
    }

    return left;
}