import looks;
import cast;
import math;
import calendar;
import mem;

struct User(int a, int b);

void main() {
    int b = 5;
    User u = User(5, a(false));
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