import math;
import looks;
import except;

void checkInt(int f, str error) {
    if(!isNumber(f) || math::floor(f) != f) {
        except::panic("Scratcher runtime type error: ${error}");
    }
}

void checkIntObf(int f) {
    if(!isNumber(f) || math::floor(f) != f) {
        except::panic("Scratcher runtime type error: Function parameter is not an integer!");
    }
}

void checkFloatObf(float f) {
    if(!isNumber(f)) {
        except::panic("Scratcher runtime type error: Function parameter is not a float!");
    }
}

void checkFloat(float f, str error) {
    if(!isNumber(f)) {
        except::panic("Scratcher runtime type error: ${error}");
    }
}

bool isNumber(float f) {
    return (f * 1) == f;
}