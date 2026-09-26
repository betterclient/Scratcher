import "../text_rendering/text_render.sc" as render;
import motion::gotoXY;
import pen;
import triangle;
import sensing;

warp void renderText(str text, int x, int y, float color, bool center) {
    pen::setSize(1.5);

    float xOffset = if(center) {
        render::textWidth(text, 15, 4) / 2
    } else 0;
    float yOffset = if(center) 20 else 0;
    gotoXY(x - xOffset, y + yOffset);

    pen::setColor(color);

    render::renderText(text, 15, 40, 4);
}

warp void fill(float x, float y, float endX, float endY, float color) {
    triangle::fill(
        x, y,
        endX, y,
        endX, endY,
        color, 1
    );

    triangle::fill(
        x, y,
        endX, endY,
        x, endY,
        color, 1
    );
}

warp void button(str text, int x, int y, int width, int height, float bgColor, float textColor, () -> void onClick) {
    fill(x, y, x + width, y + height, bgColor);
    renderText(text, x + width / 2, y + height / 2, textColor, true);

    if(sensing::isMousePressed() && isMouseInside(x, y, x + width, y + height)) {
        onClick();
    }
}

private warp bool isMouseInside(int x, int y, int endX, int endY) {
    int mx = sensing::getMouseX();
    int my = sensing::getMouseY();
    return (mx >= x && mx <= endX && my >= y && my <= endY);
}

warp void clear() {
    pen::eraseAll();
}

warp void init() {
    render::initRoboto();
}