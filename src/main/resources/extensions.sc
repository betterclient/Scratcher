import array;
import extensions_internal as self;

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
    return self::charAt(this, index);
}

warp bool str.contains(str other) {
    return self::contains(this, other);
}

warp char[] str.toCharArray() {
    char[] out = Array(char, length());
    int index = 0;
    repeat(length()) {
        out[index] = charAt(index);
        index++;
    }
    return out;
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
    if(prefix.length() > length()) {
        return false;
    }
    return substring(0, prefix.length()) == prefix;
}

warp bool str.endsWith(str suffix) {
    int offset = length() - suffix.length();
    if(offset < 0) {
        return false;
    }
    return substring(offset, length()) == suffix;
}

warp int str.indexOf(char target) {
    int index = 0;
    repeat(length()) {
        if(charAt(index) == target) {
            return index;
        }
        index++;
    }
    return -1;
}