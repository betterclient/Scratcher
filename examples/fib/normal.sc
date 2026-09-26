import sensing::ask;
import looks::say;
import cast::toInt;

on GreenFlag {
    int num = toInt(ask("Number?"));

    say("Fib: ${num} = ${fib(num)}");
}

warp int fib(int num) {
    return num if num <= 1;

    int f1 = 0;
    int f2 = 1;
    int cur = 0;

    repeat(num - 1) {
        cur = f1 + f2;
        f1 = f2;
        f2 = cur;
    }

    return cur;
}