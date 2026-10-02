import "renderer.sc"::*;
import sensing;
import math::*;

struct Player(Vec3D position, float pitch, float yaw);

warp void Player.update() {
    float deltaForward = 0;
    float deltaRight = 0;
    float deltaUp = 0;

    if (sensing::isKeyPressed("w")) deltaForward += 1;
    if (sensing::isKeyPressed("s")) deltaForward -= 1;
    if (sensing::isKeyPressed("d")) deltaRight += 1;
    if (sensing::isKeyPressed("a")) deltaRight -= 1;
    if (sensing::isKeyPressed("space")) deltaUp += 1;
    if (sensing::isKeyPressed("shift")) deltaUp -= 1;

    float speed = 0.2;

    float forwardX = sin(this.yaw);
    float forwardZ = cos(this.yaw);
    float rightX = cos(this.yaw);
    float rightZ = -sin(this.yaw);

    this.position.x += (forwardX * deltaForward + rightX * deltaRight) * speed;
    this.position.z += (forwardZ * deltaForward + rightZ * deltaRight) * speed;
    this.position.y += deltaUp * speed;

    float deltaLUp = 0;
    float deltaLRight = 0;
    if(sensing::isKeyPressed("left arrow")) deltaLRight -= 1;
    if(sensing::isKeyPressed("up arrow")) deltaLUp += 1;
    if(sensing::isKeyPressed("down arrow")) deltaLUp -= 1;
    if(sensing::isKeyPressed("right arrow")) deltaLRight += 1;

    float turnSpeed = 10;
    this.pitch += deltaLUp * turnSpeed;
    this.yaw += deltaLRight * turnSpeed;

    if (this.pitch > 89) this.pitch = 89;
    if (this.pitch < -89) this.pitch = -89;
}