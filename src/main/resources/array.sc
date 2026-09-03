import array_internal as self;
import except::panic;

//new
warp <T> T?[] arrayOfNulls(int size) {
    if(size <= 0) {
        panic("array::arrayOfNulls, size <= 0, got: ${size}");
    }

    return self::newArray(size);
}

warp <T> T[] arrayOf(int size, (int) -> T init) {
    if(size <= 0) {
        panic("array::arrayOf, size <= 0, got: ${size}");
    }

    T[] out = self::newArray(size);
    int index = 0;
    repeat(size) {
        self::replace(out, init(index), index);
        index++;
    }
    return out;
}

warp <T> T[] arrayOf(T first) {
    T[] out = self::newArray(1);
    self::replace(out, first, 0);
    return out;
}

warp <T> T[] arrayOf(T first, T second) {
    T[] out = self::newArray(2);
    self::replace(out, first, 0);
    self::replace(out, second, 1);
    return out;
}

//ops
warp <T> int T[].length() {
    return self::length(this);
}

warp operator <T> T T[].get(int index) {
    return self::itemAt(this, index); //self already does index out of bounds
}

warp operator <T> void T[].set(int index, T item) {
    self::replace(this, item, index);
}