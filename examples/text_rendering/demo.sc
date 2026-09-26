import sensing;
import triangle;
import utils::wait;
import pen;
import motion::*;
import math;
import "text_render.sc"::*;

on GreenFlag {
    initRoboto();

    str text = "Hello world! Tap to change";
    while(true) {
        pen::eraseAll();

        float x = textWidth(text, 15, 4) / 2;
        gotoXY(-x, 0);

        pen::setSize(2);
        pen::setColor(rainbow(sensing::getTimer() * 60));
        renderText(text, 15, 40, 4);

        if(sensing::isMousePressed()) {
            text = sensing::ask("New text?");
        }

        wait(0.02);
    }
}

warp float rainbow(float deg) {
    float r = (math::sin(deg) + 1.0) * 127.5;
    float g = (math::sin(deg + 120.0) + 1.0) * 127.5;
    float b = (math::sin(deg + 240.0) + 1.0) * 127.5;

    return triangle::rgb(math::round(r), math::round(g), math::round(b));
}
