import array::*;
import math::*;
import triangle;
import "player.sc"::*;
import pen;
import extensions;

struct Vec3D(float x, float y, float z);
struct Vec2D(float x, float y);
struct Triangle3D(
    Vec3D[] p,
    float color
);

struct Mesh(
    Triangle3D[] triangles
);

warp void clear() {
    pen::eraseAll();

    float color = triangle::rgb(0, 0, 0);
    triangle::fill(
        -240, -180,
         240, -180,
         240,  180,
        color, 1
    );

    triangle::fill(
        -240, -180,
         240,  180,
        -240,  180,
        color, 1
    );
}

warp Vec2D project(Vec3D v, int width, int height, float fov) {
    if (v.z <= 0.1) v.z = 0.1;

    return Vec2D(
        (v.x / v.z) * fov + (width / 2.0),
        (v.y / v.z) * fov + (height / 2.0)
    );
}

warp Vec3D rotateY(Vec3D v, float angleDeg) {
    return Vec3D(
        v.x * cos(angleDeg) + v.z * sin(angleDeg),
        v.y,
        -v.x * sin(angleDeg) + v.z * cos(angleDeg)
    );
}

warp Vec3D rotateX(Vec3D v, float angleDeg) {
    return Vec3D(
        v.x,
        v.y * cos(angleDeg) - v.z * sin(angleDeg),
        v.y * sin(angleDeg) + v.z * cos(angleDeg)
    );
}

warp Mesh createCube() {
    float red = triangle::rgb(255, 0, 0);
    float green = triangle::rgb(0, 255, 0);
    float blue = triangle::rgb(0, 0, 255);
    float yellow = triangle::rgb(255, 255, 0);
    float magenta = triangle::rgb(255, 0, 255);
    float cyan = triangle::rgb(0, 255, 255);

    Triangle3D[] tris = [
        Triangle3D([Vec3D(-0.5, -0.5, -0.5), Vec3D(-0.5, 0.5, -0.5), Vec3D(0.5, 0.5, -0.5)], red),
        Triangle3D([Vec3D(-0.5, -0.5, -0.5), Vec3D(0.5, 0.5, -0.5), Vec3D(0.5, -0.5, -0.5)], red),

        Triangle3D([Vec3D(0.5, -0.5, -0.5), Vec3D(0.5, 0.5, -0.5), Vec3D(0.5, 0.5, 0.5)], green),
        Triangle3D([Vec3D(0.5, -0.5, -0.5), Vec3D(0.5, 0.5, 0.5), Vec3D(0.5, -0.5, 0.5)], green),

        Triangle3D([Vec3D(0.5, -0.5, 0.5), Vec3D(0.5, 0.5, 0.5), Vec3D(-0.5, 0.5, 0.5)], blue),
        Triangle3D([Vec3D(0.5, -0.5, 0.5), Vec3D(-0.5, 0.5, 0.5), Vec3D(-0.5, -0.5, 0.5)], blue),

        Triangle3D([Vec3D(-0.5, -0.5, 0.5), Vec3D(-0.5, 0.5, 0.5), Vec3D(-0.5, 0.5, -0.5)], yellow),
        Triangle3D([Vec3D(-0.5, -0.5, 0.5), Vec3D(-0.5, 0.5, -0.5), Vec3D(-0.5, -0.5, -0.5)], yellow),

        Triangle3D([Vec3D(-0.5, 0.5, -0.5), Vec3D(-0.5, 0.5, 0.5), Vec3D(0.5, 0.5, 0.5)], magenta),
        Triangle3D([Vec3D(-0.5, 0.5, -0.5), Vec3D(0.5, 0.5, 0.5), Vec3D(0.5, 0.5, -0.5)], magenta),

        Triangle3D([Vec3D(-0.5, -0.5, 0.5), Vec3D(-0.5, -0.5, -0.5), Vec3D(0.5, -0.5, -0.5)], cyan),
        Triangle3D([Vec3D(-0.5, -0.5, 0.5), Vec3D(0.5, -0.5, -0.5), Vec3D(0.5, -0.5, 0.5)], cyan)
    ];

    return Mesh(tris);
}

warp void Mesh.render(float angleX, float angleY, Player camera, float fov) {
    float cosAX = cos(angleX);
    float sinAX = sin(angleX);
    float cosAY = cos(angleY);
    float sinAY = sin(angleY);
    float cy = cos(camera.yaw);
    float sy = sin(camera.yaw);
    float cp = cos(camera.pitch);
    float sp = sin(camera.pitch);

    float camX = camera.position.x;
    float camY = camera.position.y;
    float camZ = camera.position.z;

    float m00 = cosAY;
    float m01 = sinAX * sinAY;
    float m02 = cosAX * sinAY;
    float m11 = cosAX;
    float m12 = 0.0 - sinAX;
    float m20 = 0.0 - sinAY;
    float m21 = sinAX * cosAY;
    float m22 = cosAX * cosAY;

    for(auto tri in this.triangles) {
        Vec3D v0 = tri.p[0];
        Vec3D v1 = tri.p[1];
        Vec3D v2 = tri.p[2];

        float wx0 = v0.x * m00 + v0.y * m01 + v0.z * m02;
        float wy0 = v0.y * m11 + v0.z * m12;
        float wz0 = v0.x * m20 + v0.y * m21 + v0.z * m22;

        float wx1 = v1.x * m00 + v1.y * m01 + v1.z * m02;
        float wy1 = v1.y * m11 + v1.z * m12;
        float wz1 = v1.x * m20 + v1.y * m21 + v1.z * m22;

        float wx2 = v2.x * m00 + v2.y * m01 + v2.z * m02;
        float wy2 = v2.y * m11 + v2.z * m12;
        float wz2 = v2.x * m20 + v2.y * m21 + v2.z * m22;

        float dx0 = wx0 - camX;
        float dy0 = wy0 - camY;
        float dz0 = wz0 - camZ;
        float cx0 = dx0 * cy - dz0 * sy;
        float czTmp0 = dx0 * sy + dz0 * cy;
        float cy0 = dy0 * cp - czTmp0 * sp;
        float cz0 = dy0 * sp + czTmp0 * cp;

        float dx1 = wx1 - camX;
        float dy1 = wy1 - camY;
        float dz1 = wz1 - camZ;
        float cx1 = dx1 * cy - dz1 * sy;
        float czTmp1 = dx1 * sy + dz1 * cy;
        float cy1 = dy1 * cp - czTmp1 * sp;
        float cz1 = dy1 * sp + czTmp1 * cp;

        float dx2 = wx2 - camX;
        float dy2 = wy2 - camY;
        float dz2 = wz2 - camZ;
        float cx2 = dx2 * cy - dz2 * sy;
        float czTmp2 = dx2 * sy + dz2 * cy;
        float cy2 = dy2 * cp - czTmp2 * sp;
        float cz2 = dy2 * sp + czTmp2 * cp;

        continue if (cz0 <= 0.1 || cz1 <= 0.1 || cz2 <= 0.1);

        int width = 480;
        int height = 360;
        Vec2D p0 = project(Vec3D(cx0, cy0, cz0), width, height, fov);
        Vec2D p1 = project(Vec3D(cx1, cy1, cz1), width, height, fov);
        Vec2D p2 = project(Vec3D(cx2, cy2, cz2), width, height, fov);

        float cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x);
        continue if (cross >= 0);

        triangle::fill(
            clampX(p0.x), clampY(p0.y),
            clampX(p1.x), clampY(p1.y),
            clampX(p2.x), clampY(p2.y),
            tri.color, 1
        );
    }
}

private warp float clampX(float value) {
    return when {
        (value <= 0) -> -240;
        (value >= 480) -> 240;
        else -> value - 240;
    };
}

private warp float clampY(float value) {
    return when {
        (value <= 0) -> -180;
        (value >= 360) -> 180;
        else -> value - 180;
    };
}