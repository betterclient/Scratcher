import looks;
import cast;
import math;
import calendar;

struct User(int a, int b);

void main() {
    int b = 5;
    looks::say("hi! ${a(cast::toBool("true"))} ${math::floor(b)}, Today is the ${calendar::getDayOfWeek()}th day of the week!");
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