import array;
import extensions_internal as self;
import case_sensitive_equals as uppercase_helper;
import except::panic;
import math::*;

//general
warp <T, R> R T.let((T) -> R action) {
    return action(this);
}

warp <T> T T.also((T) -> void action) {
    action(this);
    return this;
}

//string
warp bool str.isEmpty() {
    return length() == 0;
}

warp bool str.isNotEmpty() {
    return length() > 0;
}

warp str str.concat(str right) {
    return self::concat(this, right);
}

warp int str.length() {
    return self::length(this);
}

warp char str.charAt(int index) {
    return self::charAt(this, index + 1);
}

warp bool str.contains(str other) {
    return true if(other.length() == 0);
    return false if(other.length() > length());

    int maxStart = length() - other.length();

    repeat(maxStart + 1) { i ->
        if(substring(i, i + other.length()) === other) {
            return true;
        }
    }

    return false;
}

warp bool str.containsIgnoreCase(str other) {
    return self::contains(this, other);
}

warp char[] str.toCharArray() {
    return array::arrayOf(length(), (int index) -> {
        return charAt(index);
    });
}

warp str str.substring(int from, int to) {
    str out = "";
    int index = from;

    repeat(to - from) {
        out = out.concat(charAt(index));
        index++;
    }

    return out;
}

warp str str.substringInclusive(int fromInclusive, int toInclusive) {
    return substring(fromInclusive, toInclusive + 1);
}

warp bool str.startsWith(str prefix) {
    return false if(prefix.length() > length());
    return substring(0, prefix.length()) === prefix;
}

warp bool str.endsWith(str suffix) {
    int offset = length() - suffix.length();
    if(offset < 0) {
        return false;
    }
    return substring(offset, length()) === suffix;
}

warp int str.indexOf(char target) {
    repeat(length()) { index ->
        if(charAt(index) === target) {
            return index;
        }
    }
    return -1;
}

warp bool char.isUppercase() {
    return uppercase_helper::isUppercase(this);
}

//arrays
warp <T> void T[].copyTo(T[] other) {
    if(other.length() < this.length()) {
        panic("Extensions, copyTo: ${other.length()} < ${this.length()}");
    }

    for(T t in this) { index ->
        other[index] = t;
    }
}

warp <T, R> R[] T[].map((T) -> R action) {
    return array::arrayOf(this.length(), (int index) -> {
        return action(this[index]);
    });
}

warp <T> void T[].forEach((T) -> void action) {
    for(T t in this) {
        action(t);
    }
}

warp <T> void T[].sort((T, T) -> int compare) {
    return if length() <= 1;

    this.sortInternal(0, length() - 1, compare);
}

private warp <T> void T[].sortInternal(int l, int h, (T, T) -> int compare) {
    int low = l;
    int high = h;
    while(low < high) {
        T pivot = this[round(floor(low + (high - low) / 2))];
        int i = low;
        int j = high;

        while(i <= j) {
            while(compare(this[i], pivot) < 0) { i += 1; }
            while(compare(this[j], pivot) > 0) { j -= 1; }
            if(i <= j) {
                T temp = this[i];
                this[i] = this[j];
                this[j] = temp;
                i += 1;
                j -= 1;
            }
        }

        if(j - low < high - i) {
            if(low < j) this.sortInternal(low, j, compare);
            low = i;
        } else {
            if(i < high) this.sortInternal(i, high, compare);
            high = j;
        }
    }
}