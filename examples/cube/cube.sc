import math::*;
import cast::*;
import extensions;
import "renderer.sc";
import utils::wait;
import "player.sc";

on GreenFlag {
    auto cube = renderer::createCube();
    auto player = player::Player(Vec3D(0, 0, -3), 0, 0);

    float angle = 0.0;

    while(true) {
        renderer::clear();
        player.update();

        angle += 1.5;
        if (angle >= 360) {
            angle -= 360;
        }

        cube.render(angle * 0.5, angle, player, 400);

        wait(0);
    }
}