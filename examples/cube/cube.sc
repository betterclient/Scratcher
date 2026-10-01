import math::*;
import cast::*;
import extensions;
import "renderer.sc";
import utils::wait;

on GreenFlag {
    auto cube = renderer::createCube();
    float angle = 0.0;

    while(true) {
        renderer::clear();

        angle += 1.5;
        if (angle >= 360) {
            angle -= 360;
        }

        cube.render(angle * 0.5, angle, 400);

        wait(0);
    }
}