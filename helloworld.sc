import looks;
import cast;
import sensing;
import mem;

struct User(int a, int b);

void main() {
    int b = 5;
    User u = User(5, cast::toIntOrDefault(sensing::ask("What should I print?"), 5));
    looks::say("test: ${u.b}");
    mem::free(u);
}

warp int a(bool a) {
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