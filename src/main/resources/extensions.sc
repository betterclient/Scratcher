import list;
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
    char[] out = List(char);
    int index = 0;
    repeat(length()) {
        out.add(charAt(index));
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

//list
warp <T> int T[].length() {
    return list::length(this);
}

warp <T> void T[].forEach((T) -> void action) {
    for(auto t in this) {
        action(t);
    }
}

warp <T> void T[].forEachIndexed((int, T) -> void action) {
    int index = 0;
    for(auto t in this) {
        action(index, t);
        index++;
    }
}

warp <T, R> R[] T[].map((T) -> R action) {
    R[] out = List(R);
    list::reserve(out, list::length(this));
    for(auto t in this) {
        out.add(action(t));
    }
    return out;
}

warp <T, R> R[] T[].mapIndexed((int, T) -> R action) {
    R[] out = List(R);
    list::reserve(out, list::length(this));
    int index = 0;
    for(auto t in this) {
        out.add(action(index, t));
        index++;
    }
    return out;
}

warp <T> void T[].add(T item) {
    list::add(this, item);
}

warp <T> void T[].remove(T item) {
    list::remove(this, item);
}

warp <T> void T[].replaceAll((T) -> T action) {
    int index = 0;
    for(auto t in this) {
        this[index] = action(t);

        index++;
    }
}

warp <T> T[] T[].filter((T) -> bool action) {
    T[] out = List(T);
    list::reserve(out, list::length(this));
    for(auto t in this) {
        if(action(t)) {
            out.add(t);
        }
    }
    return out;
}

warp <T> T[] T[].filterNot((T) -> bool action) {
    return filter(T t -> !action(t));
}

warp <T> T[] T?[].filterNotNull() {
    T[] out = List(T);
    list::reserve(out, list::length(this));
    for(auto t in this) {
        if(t != null) {
            out.add(t!!);
        }
    }
    return out;
}

warp <T> bool T[].any((T) -> bool action) {
    for(auto t in this) {
        return true if(action(t));
    }

    return false;
}

warp <T> bool T[].none((T) -> bool action) {
    return !any(action);
}

warp <T> int T[].count((T) -> bool action) {
    int amount = 0;
    for(auto t in this) {
        if(action(t)) {
            amount++;
        }
    }
    return amount;
}

warp <T> bool T[].isEmpty() {
    return length() == 0;
}

warp <T> bool T[].isNotEmpty() {
    return length() > 0;
}

warp <T> bool T[].all((T) -> bool action) {
    for(auto t in this) {
        if(!action(t)) {
            return false;
        }
    }
    return true;
}

warp <T> bool T[].contains(T item) {
    for(auto t in this) {
        if(t == item) {
            return true;
        }
    }
    return false;
}

warp <T> int T[].indexOf(T item) {
    int index = 0;
    for(auto t in this) {
        if(t == item) {
            return index;
        }
        index++;
    }
    return -1;
}

warp <T> T? T[].firstOrNull() {
    if(isEmpty()) {
        return null;
    }
    return this[0];
}

warp <T> T? T[].lastOrNull() {
    if(isEmpty()) {
        return null;
    }
    return this[length() - 1];
}

warp <T> T? T[].find((T) -> bool action) {
    for(auto t in this) {
        if(action(t)) {
            return t;
        }
    }
    return null;
}

warp <T, R> R[] T[].flatMap((T) -> R[] transform) {
    R[] out = List(R);
    for(auto t in this) {
        for(auto item in transform(t)) {
            out.add(item);
        }
    }
    return out;
}

warp <T> T[] T[].take(int n) {
    T[] out = List(T);
    int count = 0;
    for(auto t in this) {
        return out if(count >= n);

        out.add(t);
        count++;
    }
    return out;
}

warp <T> T[] T[].drop(int n) {
    T[] out = List(T);
    int index = 0;
    for(auto t in this) {
        if(index >= n) {
            out.add(t);
        }
        index++;
    }
    return out;
}

warp <T> T[] T[].reversed() {
    T[] out = List(T);
    list::reserve(out, length());
    int index = length() - 1;
    repeat(length()) {
        out.add(this[index]);
        index--;
    }
    return out;
}