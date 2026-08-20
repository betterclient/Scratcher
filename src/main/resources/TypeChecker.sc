import except;
import math;
import cast;

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

//this is a terrible solution and I hate it
warp void checkChar(char f, str error) {
    if(cast::toCharOrDefault(cast::toStr(f), 'g') == 'g' && cast::toCharOrDefault(cast::toStr(f), 'f') == 'f') {
        except::panic("Scratcher runtime type error: ${error}");
    }
}


warp void checkCharObf(char f) {
    if(cast::toCharOrDefault(cast::toStr(f), 'g') == 'g' && cast::toCharOrDefault(cast::toStr(f), 'f') == 'f') {
        except::panic("Scratcher runtime type error: Function parameter is not a char!");
    }
}