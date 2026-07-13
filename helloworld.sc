import triangle;
import utils;
import looks;
import list;
import pen;
import sensing;
import except;

struct Triangle(float x1, float y1, float x2, float y2, float x3, float y3);
enum TriangleRenderMode(OFF, RENDER, ITHINK);

auto a = Triangle(5, 15, 60, -120, 240, 67);

on GreenFlag {
    auto mode = when(sensing::ask("mode")) {
        "off" -> TriangleRenderMode.OFF
        "render" -> TriangleRenderMode.RENDER
        else -> {
            looks::say("If thinking is your power, what are you without it?");
            except::panic("If thinking is your power, what are you without it?");

            null;
        }
    };

    auto triangles = List(Triangle);
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
        if(mode == TriangleRenderMode.OFF) {
            5
        } else {
            triangles[14].y3
        }
    );

    while(true) {
        pen::eraseAll();
        when(mode) {
            TriangleRenderMode.OFF -> {}
            TriangleRenderMode.RENDER -> render(triangles);
            else -> looks::say("How are you here???");
        };
    }
}

warp void render(Triangle[] triangles) {
    looks::say("X1 ${a.x1}");
    a.x1++;
    for(auto t in triangles) {
        triangle::fill(
            t.x1, t.y1, t.x2, t.y2, t.x3, t.y3, triangle::rgb(utils::random(0, 255), utils::random(0, 255), utils::random(0, 255)), 1
        );
    }
}