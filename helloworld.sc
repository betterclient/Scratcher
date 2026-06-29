import triangle;
import random;
import looks;
import list;
import pen;

struct Triangle(float x1, float y1, float x2, float y2, float x3, float y3);

on GreenFlag {
    Triangle[] triangles = List(Triangle);
    repeat(150) {
        list::add(triangles, Triangle(
            random::random(-240, 240),
            random::random(-180, 180),
            random::random(-240, 240),
            random::random(-180, 180),
            random::random(-240, 240),
            random::random(-180, 180)
        ));
    }
    while(true) {
        pen::eraseAll();
        render(triangles);
    }
}

warp void render(Triangle[] triangles) {
    int len = list::length(triangles);
    int i = 0;
    while (i < len) {
        Triangle t = list::itemAt(triangles, i);

        triangle::fill(
            t.x1, t.y1, t.x2, t.y2, t.x3, t.y3, triangle::rgb(random::random(0, 255), random::random(0, 255), random::random(0, 255)), 1
        );

        i = i + 1;
    }
}