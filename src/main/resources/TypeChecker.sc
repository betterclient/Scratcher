import math;
import except;

void checkInt(int f, str error) {
    if(!math::isNumber(f) || math::floor(f) != f) {
        except::panic("Scratcher runtime type error: ${error}");
    }
}

void checkIntObf(int f) {
    if(!math::isNumber(f) || math::floor(f) != f) {
        except::panic("Scratcher runtime type error: Function parameter is not an integer!");
    }
}

void checkFloatObf(float f) {
    if(!math::isNumber(f)) {
        except::panic("Scratcher runtime type error: Function parameter is not a float!");
    }
}

void checkFloat(float f, str error) {
    if(!math::isNumber(f)) {
        except::panic("Scratcher runtime type error: ${error}");
    }
}