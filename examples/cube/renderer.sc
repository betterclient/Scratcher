import array::*;
import math::*;
import triangle;
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

warp void Mesh.render(float angleX, float angleY, float fov) {
    for(auto tri in this.triangles) {
        Vec3D[] transformed = tri.p.map(
            (Vec3D v) -> {
                Vec3D v2 = rotateX(v, angleX);
                v2 = rotateY(v2, angleY);

                v2.z += 2.5;
                return v2;
            }
        );

        int width = 480;
        int height = 360;
        Vec2D p0 = project(transformed[0], width, height, fov);
        Vec2D p1 = project(transformed[1], width, height, fov);
        Vec2D p2 = project(transformed[2], width, height, fov);

        float cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x);
        continue if (cross >= 0);

        triangle::fill(
            p0.x - 240, p0.y - 180,
            p1.x - 240, p1.y - 180,
            p2.x - 240, p2.y - 180,
            tri.color, 1
        );
    }
}