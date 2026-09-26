import sensing::ask;
import looks::say;
import cast::toInt;

on GreenFlag {
    int num = toInt(ask("Number?"));

    say("Fib: ${num} = ${fib(num)}");
}

warp int fib(int num) {
    return num if num <= 1;

    return fib(num - 1) + fib(num - 2);
}