import triangle;
import utils;
import looks;
import list;
import pen;
import gc;
import sensing;
import string;

struct Triangle(float x1, float y1, float x2, float y2, float x3, float y3);

Triangle a = Triangle(5, 15, 60, -120, 240, 67);

on GreenFlag {
    Triangle[] triangles = List(Triangle);
    repeat(150) {
        list::add(triangles, Triangle(
            utils::random(-240, 240),
            utils::random(-180, 180),
            utils::random(-240, 240),
            utils::random(-180, 180),
            utils::random(-240, 240),
            utils::random(-180, 180)
        ));
    }
    triangles[15] = Triangle(
        utils::random(-240, 240),
        utils::random(-180, 180),
        utils::random(-240, 240),
        utils::random(-180, 180),
        utils::random(-240, 240),
        triangles[14].y3
    );
    if(triangles[14].y1 >= 15) {
        looks::say(">=15");
    } else if(triangles[14].y2 >= 155) {
        looks::say("<=15");
    } else {
        looks::say("Both false!");
    }
    while(true) {
        pen::eraseAll();
        render(triangles);
    }
}

warp void render(Triangle[] triangles) {
    looks::say("X1 ${a.x1}");
    for(Triangle t in triangles) {
        triangle::fill(
            t.x1, t.y1, t.x2, t.y2, t.x3, t.y3, triangle::rgb(utils::random(0, 255), utils::random(0, 255), utils::random(0, 255)), 1
        );
    }
}