import triangle;
import random;
import looks;
import pen;

on GreenFlag {
    while(true) {
        pen::eraseAll();
        render();
    }
}

warp void render() {
    repeat(1500) {
        triangle::outline(
            random::random(-240, 240),
            random::random(-180, 180),
            random::random(-240, 240),
            random::random(-180, 180),
            random::random(-240, 240),
            random::random(-180, 180),
            triangle::rgb(random::random(0, 255), random::random(0, 255), random::random(0, 255)),
            1
        );
    }
}