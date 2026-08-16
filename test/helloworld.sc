import triangle;
import utils;
import looks;
import list;
import pen;
import sensing;
import except;
import "fps.sc" as counter;
import cast;

struct Triangle<Num>(Num x1, Num y1, Num x2, Num y2, Num x3, Num y3);
enum TriangleRenderMode(OFF, RENDER, ITHINK);

Triangle<float> a = Triangle(5, 15, 60, -120, 240, 67);

on GreenFlag {
    auto mode = when(sensing::ask("mode")) {
        "off" -> TriangleRenderMode.OFF
        "render" -> TriangleRenderMode.RENDER
        else -> {
            looks::say("If thinking is your power, what are you without it?");
            //except::panic("If thinking is your power, what are you without it?");

            null;
        }
    };

    auto triangles = List(Triangle<float>?);
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
            triangles[14]?.y3 ?: 0
        }
    );

    while(true) {
        pen::eraseAll();
        render(mode?: TriangleRenderMode.OFF, triangles);
        //counter::update();
        //looks::say("FPS: ${counter::get()}");
    }
}

warp void render(TriangleRenderMode mode, Triangle<float>?[] triangles) {
    a.x1++;
    auto amountRendered = 0;

    forEach(triangles, when(mode) {
        TriangleRenderMode.RENDER -> (Triangle<float>? tri) -> {
            return if(tri?.x1 == null);
            auto rt = tri!!;

            triangle::fill(
                rt.x1, rt.y1, rt.x2, rt.y2, rt.x3, rt.y3, triangle::rgb(utils::random(0, 255), utils::random(0, 255), utils::random(0, 255)), 1
            );
            amountRendered += 1;
        };
        else -> &noRender;
    });

    looks::say("Amount rendered ${amountRendered}");
}

warp str? hello() {
    return null if(utils::random(0, 5) == 4);
    return "yo!";
}

warp void noRender(Triangle<float>? t) {}

warp <T> void forEach(T[] a, (T) -> void action) {
    for(T t in a) {
        action(t);
    }
}