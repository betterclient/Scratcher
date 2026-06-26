import except;
import math;

warp void checkInt(int f, str error) {
    if((f * 1) != f || math::floor(f) != f) {
        except::panic("Scratcher runtime type error: ${error}");
    }
}

warp void checkIntObf(int f) {
    if((f * 1) != f || math::floor(f) != f) {
        except::panic("Scratcher runtime type error: Function parameter is not an integer!");
    }
}

warp void checkFloatObf(float f) {
    if((f * 1) != f) {
        except::panic("Scratcher runtime type error: Function parameter is not a float!");
    }
}

warp void checkFloat(float f, str error) {
    if((f * 1) != f) {
        except::panic("Scratcher runtime type error: ${error}");
    }
}