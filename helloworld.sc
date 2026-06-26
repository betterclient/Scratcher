import pen;
import motion;
import math;
import random;

warp void fillTriDecl(
    float x1, float y1,
    float x2, float y2,
    float x3, float y3,
    float color, float resolution
) {
    pen::setColor(color);

    float side23 = math::sqrt((x2 - x3) * (x2 - x3) + (y2 - y3) * (y2 - y3));
    float side13 = math::sqrt((x1 - x3) * (x1 - x3) + (y1 - y3) * (y1 - y3));
    float side12 = math::sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));

    float halfDia = (side23 + side13 + side12) / 2.0;
    float dia = 2.0 * math::sqrt(
        ((halfDia - side23) * (halfDia - side13) * (halfDia - side12)) / halfDia
    );
    halfDia = halfDia + halfDia;

    float xPos = ((side23 * x1) + (side13 * x2) + (side12 * x3)) / halfDia;
    float yPos = ((side23 * y1) + (side13 * y2) + (side12 * y3)) / halfDia;

    motion::gotoXY(xPos, yPos);
    pen::setSize(dia);
    pen::down();

    if (dia > 0.0) {
        if ((side13 < side23) || (side12 < side23)) {
            if (side12 < side13) {
                halfDia = xPos - x3;
                side23 = yPos - y3;
            } else {
                halfDia = xPos - x2;
                side23 = yPos - y2;
            }
        } else {
            halfDia = xPos - x1;
            side23 = yPos - y1;
        }

        halfDia = math::sqrt((halfDia * halfDia) + (side23 * side23)) / (dia / 2.0);

        float tri8 = ((halfDia * resolution) / (halfDia - 1.0)) + 0.25;

        halfDia = 0.5 - (0.5 / halfDia);
        side23 = (xPos - x1) / dia;
        side13 = (yPos - y1) / dia;
        side12 = (xPos - x2) / dia;

        float tri5 = (yPos - y2) / dia;
        float tri6 = (xPos - x3) / dia;
        float tri7 = (yPos - y3) / dia;

        while (dia >= tri8) {
            dia = halfDia * dia;
            pen::setSize(dia + 0.5);
            motion::gotoXY(x1 + (dia * side23), y1 + (dia * side13));
            motion::gotoXY(x2 + (dia * side12), y2 + (dia * tri5));
            motion::gotoXY(x3 + (dia * tri6), y3 + (dia * tri7));
            motion::gotoXY(x1 + (dia * side23), y1 + (dia * side13));
        }
    }

    pen::setSize(resolution);
    motion::gotoXY(x1, y1);
    motion::gotoXY(x2, y2);
    motion::gotoXY(x3, y3);
    motion::gotoXY(x1, y1);
    pen::up();
}

float rgb(float r, float g, float b) {
    float colorValue = (r * 65536.0) + (g * 256.0) + b;
    return colorValue;
}

on GreenFlag {
    int index = 0;
    while(true) {
        fillTriDecl(
            random::random(-240, 240),
            random::random(-180, 180),
            random::random(-240, 240),
            random::random(-180, 180),
            random::random(-240, 240),
            random::random(-180, 180),
            rgb(random::random(0, 255), random::random(0, 255), random::random(0, 255)),
            1
        );
        if(index == 50) {
            pen::eraseAll();
            index = 0;
        }
        index = index + 1;
    }
}