import looks;

struct User(int a, int b);

void main() {
    int b = 5;
    looks::say("hi! ${a()} ${b}");
}

int a() {
    return 5;
}

on GreenFlag {
    main();
}

on KeyPressed(W) {
    looks::say("W was pressed!");
}