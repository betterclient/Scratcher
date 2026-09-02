import array_internal as self;

//new
warp <T> T?[] arrayOfNulls(int size) {
    return self::newArray(size);
}

warp <T> T[] arrayOf(int size, (int) -> T init) {
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

warp <T> T T[].itemAt(int index) {
    return self::itemAt(this, index); //self already does index out of bounds
}

warp <T> void T[].replace(int index, T item) {
    self::replace(this, item, index);
}