import extensions;
import list::*;
import except::panic;
import cast;

enum MathOperation(ADD, SUB, MUL, DIV, MOD, POW);

sealed enum LexerToken {
    Int(int value),
    Float(float value),
    Identifier(str text),
    Operation(MathOperation op),
    LeftParen,
    RightParen,
    Comma,
    EOF
}

warp List<LexerToken> lex(str code) {
    List<LexerToken> out = newList();
    auto parseInfo = LexerParseInfo(out, code, 0);

    parseInfo.parse();
    return out;
}

struct LexerParseInfo(List<LexerToken> tokens, str code, int index);

private warp void LexerParseInfo.parse() {
    while(!isAtEnd()) {
        char c = advance();

        when(c) {
            ' ' -> {}
            '+' -> this.tokens.add(LexerToken.Operation(MathOperation.ADD));
            '-' -> this.tokens.add(LexerToken.Operation(MathOperation.SUB));
            '*' -> this.tokens.add(LexerToken.Operation(MathOperation.MUL));
            '/' -> this.tokens.add(LexerToken.Operation(MathOperation.DIV));
            '%' -> this.tokens.add(LexerToken.Operation(MathOperation.MOD));
            '^' -> this.tokens.add(LexerToken.Operation(MathOperation.POW));
            '(' -> this.tokens.add(LexerToken.LeftParen);
            ')' -> this.tokens.add(LexerToken.RightParen);
            ',' -> this.tokens.add(LexerToken.Comma);

            else -> {
                if (c.isDigit()) {
                    readNumber(c);
                } else if (c.isAlpha()) {
                    readIdentifier(c);
                } else {
                    panic("Unknown token: ${c}");
                }
            }
        }
    }
    this.tokens.add(LexerToken.EOF);
}

private warp void LexerParseInfo.readNumber(char first) {
    str buffer = first;
    bool isFloat = false;

    while (!isAtEnd()) {
        char? next = peek();
        if (next == null) break;

        if (next!!.isDigit()) {
            buffer = "${buffer}${advance()}";
        } else if (next == '.' && !isFloat) {
            isFloat = true;
            buffer = "${buffer}${advance()}";
        } else {
            break;
        }
    }

    if (isFloat) {
        this.tokens.add(LexerToken.Float(cast::toFloat(buffer)));
    } else {
        this.tokens.add(LexerToken.Int(cast::toInt(buffer)));
    }
}

private warp void LexerParseInfo.readIdentifier(char first) {
    str buffer = first;

    while (!isAtEnd()) {
        char? next = peek();
        if (next == null) break;

        if (next!!.isAlpha() || next!!.isDigit()) {
            buffer = "${buffer}${advance()}";
        } else {
            break;
        }
    }

    this.tokens.add(LexerToken.Identifier(buffer));
}

private warp bool LexerParseInfo.isAtEnd() {
    return this.index >= this.code.length();
}

private warp char? LexerParseInfo.peek() {
    return null if this.isAtEnd();

    return this.code.charAt(this.index);
}

private warp char LexerParseInfo.advance() {
    auto c = this.peek()!!;
    this.index = this.index + 1;
    return c;
}

//no char comparison :(
private warp bool char.isDigit() {
    return when(this) {
        '0' -> true
        '1' -> true
        '2' -> true
        '3' -> true
        '4' -> true
        '5' -> true
        '6' -> true
        '7' -> true
        '8' -> true
        '9' -> true
        else -> false
    };
}

private warp bool char.isAlpha() {
    return when(this) {
        'a' -> true
        'b' -> true
        'c' -> true
        'd' -> true
        'e' -> true
        'f' -> true
        'g' -> true
        'h' -> true
        'i' -> true
        'j' -> true
        'k' -> true
        'l' -> true
        'm' -> true
        'n' -> true
        'o' -> true
        'p' -> true
        'q' -> true
        'r' -> true
        's' -> true
        't' -> true
        'u' -> true
        'v' -> true
        'w' -> true
        'x' -> true
        'y' -> true
        'z' -> true
        else -> false
    };
}