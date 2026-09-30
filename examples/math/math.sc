import "lexer.sc";
import "ast.sc";
import "interpreter.sc";
import sensing::ask;
import utils::wait;

on GreenFlag {
    str ans = ask("Math?");
    while(ans != "done") {
        auto compilation = compile(ans);

        ans = ask(
            if(compilation == null) {
                "Empty?"
            } else {
                "Answer: ${interpreter::run(compilation!!)}"
            }
        );
        wait(0);
    }
}

warp ast::Expr? compile(str code) {
    auto tokens = lexer::lex(code);
    auto expr = tokens.parse();

    return expr;
}