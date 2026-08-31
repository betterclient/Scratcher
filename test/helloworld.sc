import triangle::{fill, rgb};
import utils::random;
import looks;
import pen;
import sensing;
import extensions;
import list::*;
import "fps.sc" as counter;

//standard library

//option 1
import triangle::{fill, rgb};
import utils::random;
import list::*;

//option 2
import {fill, rgb} from triangle;
import random from utils;
import * from list;

//option 3
from triangle import {fill, rgb};
from utils import random;
from list import *;

//user code

//option 1
import "fps.sc"::*;
import "../hello.sc"::random;

//option 2
import * from "fps.sc";
import random from "../hello.sc";

//option 3
from "fps.sc" import *;
from "../hello.sc" import random;

struct Triangle<Num>(Num x1, Num y1, Num x2, Num y2, Num x3, Num y3);
enum TriangleRenderMode(OFF, RENDER, ITHINK);

Triangle<float> a = Triangle(5, 15, 60, -120, 240, 67);

on GreenFlag {
    auto mode = when(sensing::ask("mode")) {
        "off" -> TriangleRenderMode.OFF
        "render" -> TriangleRenderMode.RENDER
        else -> {
            looks::say("If thinking is your power, what are you without it?");
            null;
        }
    };

    List<Triangle<float>?> triangles = newList();
    repeat(150) {
        triangles.add(Triangle(
            random(-240, 240),
            random(-180, 180),
            random(-240, 240),
            random(-180, 180),
            random(-240, 240),
            random(-180, 180)
        ));
    }

    triangles[15] = Triangle(
        random(-240, 240),
        random(-180, 180),
        random(-240, 240),
        random(-180, 180),
        random(-240, 240),
        if(mode == TriangleRenderMode.OFF) {
            5
        } else {
            triangles[14]?.y3 ?: 0
        }
    );

    while(true) {
        pen::eraseAll();
        render(mode?: TriangleRenderMode.OFF, triangles);
        counter::update();
        looks::say("FPS: ${counter::get()}");
    }
}

warp void render(TriangleRenderMode mode, List<Triangle<float>?> triangles) {
    a.x1++;
    auto amountRendered = 0;

    if(triangles.contains(a)) {
        looks::say("hiiiiiii");
    }

    triangles.forEach((Triangle<float>? t) -> {
        t?.x1?.let((float x1) -> {
            looks::say("boo! ${x1}");
        });
    });

    triangles
        .filterNotNull()
        .filter((Triangle<float> t) -> t.x1 > 150)
        .filter((Triangle<float> t) -> t.y1 > 150)
        .forEach(
            when(mode) {
                TriangleRenderMode.RENDER -> (Triangle<float> tri) -> {
                    fill(
                        tri.x1, tri.y1, tri.x2, tri.y2, tri.x3, tri.y3, rgb(random(0, 255), random(0, 255), random(0, 255)), 1
                    );
                    amountRendered += 1;
                };
                else -> &noRender;
            }
        );

    looks::say("Amount rendered ${amountRendered}");
}

warp void noRender(Triangle<float> t) {}

export warp void printFPS() {
    looks::say("${counter::get()}");
}