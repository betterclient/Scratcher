import looks;

struct User(int a, int b);

void main() {
    int b = 5;
    looks::say("hi! ${a(true)} ${b}");
}

int a(bool a) {
    if(a) {
        return 5;
    } else {
        return 67;
    }
}

on GreenFlag {
    main();
}

on KeyPressed(W) {
    looks::say("W was pressed!");
}