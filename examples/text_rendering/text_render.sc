//text renderer implementation ported from https://turbowarp.org/389246040/
import pen;
import motion::*;
import math;
import cast;
import extensions::*;

private static str[] font;
private static str[] fontIndex;
private static str[] fontWidths;

private str characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789`~!@#$%^&*()-=_+[]\\{}|;':\",./<>?";

private str PEN_UP = "-1";

warp void init() {
    loadFont("010.5011,0.20.50.80.5 0001,0010.110.500.510.61101 10000111 0010.1110100 0001,0010,00.510.5,0111 0001,0010,00.510.5 10.100011110.50.50.5 0001,00.510.5,1011 0.500.51 101101 0001,00.510,00.511 000111 01000.511011 01001110 0010110100 01001010.500.5 0010110100,0.50.511 010010.110.500.511 100000.510.51101 0.500.51,0010 00011110 000.5110 00010.501110 0011,0110 000.50.510,0.50.50.51 00100111 10.600.6011110.300.3 00011110.300.3 10.300.30111 10.300.3011110 00.610.610.300.30111 100.500.51,00.310.3 10.600.600.210.21101 0001,00.310.311 00.601,00.300.2 0.50.50.510.21,0.50.10.50 0001,00.50.80.5,00.511 0001 0100.310.311,0.50.30.51 0100.310.311 00.310.3110100.3 00.610.610.200.201 10.600.600.210.211 0100.310.3 10.300.300.610.61101 0.500.5111,00.310.3 00.3011110.3 00.30.5110.3 00.3010.50.31110.3 00.311,10.301 10.600.600.2,10.21101 00.310.30111 101101001001 0001 001010.500.50111 00101101,00.510.5 0000.510.5,1011 100000.510.51101 1000011110.500.5 001011 00.510.51000011110.5 10.500.500101101 000.10.1 00.50.20.40.40.40.60.50.80.610.6 0000.6,00.9 0.50.40.50.610.610000111 0.200.21,0.800.81,00.210.2,00.810.8 100000.510.51101,0.500.51 1001,00,11 00.50.5010.5 0.50.5 0.500.50.4,0.20.20.80.2 0.2000.200.80.21 000.20.20.20.801 00.510.5 00.310.3,00.710.7 0111 00.510.5,0.50.20.50.8 0.2000010.21 000.200.2101 000.21 0.2000.20.20.500.80.21 000.20.200.50.20.801 0.200.21 0.20.2,0.20.700.9 0000.2 00.2,00.8 0000.2,0.500.50.2 0.20801 01 0.2001 10.200.510.8 00.210.500.8 001010.50.50.50.50.7,0.51 ");
}

warp int getCharIndex(char target) {
    int len = characters.length();
    int j = 0;
    while (j < len) {
        if (characters.charAt(j) === target) {
            return j;
        }
        j++;
    }
    return -1;
}

warp void loadFont(str fontData) {
    fontIndex.clear();
    fontWidths.clear();
    font.clear();

    fontIndex.add("1");
    float xoffset = 0.0;
    float currentX = 0.0;
    bool isX = true;
    str currentNum = "";

    int i = 0;
    int dataLen = fontData.length();

    while (i < dataLen) {
        char c = fontData.charAt(i);

        if (c == ' ') {
            fontIndex.add(cast::toStr(font.length() + 1));
            if (xoffset == 0.0) {
                xoffset = 0.2;
            }
            fontWidths.add(cast::toStr(xoffset));
            xoffset = 0.0;
        } else if (c == ',') {
            font.add(PEN_UP);
            font.add(PEN_UP);
        } else {
            char nextC = if (i + 1 < dataLen) fontData.charAt(i + 1) else ' ';

            if (c == '0' && nextC == '.') {
                i++;
                currentNum = "0.";
            } else {
                currentNum = "${currentNum}${cast::toStr(c)}";

                if (isX) {
                    currentX = cast::toFloat(currentNum);
                    isX = false;
                    if (currentX > xoffset) {
                        xoffset = currentX;
                    }
                } else {
                    float currentY = cast::toFloat(currentNum);
                    font.add(cast::toStr(currentX));
                    font.add(cast::toStr(currentY));
                    isX = true;
                }
                currentNum = "";
            }
        }
        i++;
    }
}

warp float textWidth(str text, float w, float gap) {
    float totalWidth = 0 - gap;
    int l = 0;
    int textLen = text.length();

    while (l < textLen) {
        char letter = text.charAt(l);
        float charWidth = 0.4;

        if (letter != ' ') {
            int letterIndex = getCharIndex(letter);
            int listIdx = letterIndex + 1;
            if (letterIndex >= 0 && listIdx <= fontWidths.length()) {
                charWidth = cast::toFloat(fontWidths[listIdx]);
            }
        }

        totalWidth = totalWidth + math::round((charWidth * w) + gap);
        l++;
    }

    if (totalWidth < 0.0) {
        return 0.0;
    }
    return totalWidth;
}

warp void renderText(str text, float w, float h, float gap) {
    pen::up();
    float startX = getX();
    float startY = getY();
    float xoffset = 0.0;

    int l = 0;
    int textLen = text.length();

    while (l < textLen) {
        char letter = text.charAt(l);
        float charWidth = 0.4;

        if (letter != ' ') {
            int letterIndex = getCharIndex(letter);
            int listIdx = letterIndex + 1;

            if (letterIndex >= 0 && (listIdx + 1) <= fontIndex.length()) {
                charWidth = cast::toFloat(fontWidths[listIdx]);
                int i = cast::toInt(fontIndex[listIdx]);
                int end = cast::toInt(fontIndex[listIdx + 1]);

                pen::up();
                while (i < end) {
                    if (font[i] == PEN_UP) {
                        pen::up();
                    } else {
                        float fx = cast::toFloat(font[i]);
                        float fy = cast::toFloat(font[i + 1]);
                        gotoXY(
                            startX + (fx * w + xoffset),
                            startY + ((0.0 - fy) * h)
                        );
                        pen::down();
                        gotoX(startX + (fx * w + xoffset) + 0.1);
                    }
                    i = i + 2;
                }
            }
        }

        xoffset = xoffset + math::round((charWidth * w) + gap);
        l++;
    }

    pen::up();
    gotoXY(startX, startY);
}