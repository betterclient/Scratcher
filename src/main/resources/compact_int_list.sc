import extensions;
import list::*;
import array::*;
import cast;
import except;

struct IntList(
    private str data,
    private int length
);

warp IntList newIntList() {
    return IntList("", 0);
}

warp IntList List<int>.toCompact() {
    return IntList(this.joinToString(",", (int obj) -> {
        return cast::toStr(obj);
    }), this.length());
}

warp int IntList.length() {
    return this.length;
}

warp bool IntList.isEmpty() {
    return this.length == 0;
}

warp bool IntList.isNotEmpty() {
    return this.length > 0;
}

warp void IntList.add(int item) {
    if(this.length == 0) {
        this.data = "${item}";
    } else {
        this.data = "${this.data},${item}";
    }
    this.length++;
}

warp void IntList.forEach((int) -> void action) {
    return if(this.length == 0);
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            action(cast::toInt(current));
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    if(current.isNotEmpty()) {
        action(cast::toInt(current));
    }
}

warp List<int> IntList.fromCompact() {
    List<int> out = newList();
    this.forEach((int item) -> {
        out.add(item);
    });
    return out;
}

warp operator int IntList.get(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("IntList.get: index out of bounds: ${index} (length: ${this.length})");
    }

    int currentIndex = 0;
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            if(currentIndex == index) {
                return cast::toInt(current);
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }

    return cast::toInt(current);
}

warp operator void IntList.set(int index, int item) {
    if(index < 0 || index >= this.length) {
        except::panic("IntList.set: index out of bounds: ${index} (length: ${this.length})");
    }

    str newData = "";
    int currentIndex = 0;
    str current = "";
    str replacement = "${item}";

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            str valStr = if(currentIndex == index) replacement else current;
            newData = if(newData.isEmpty()) valStr else "${newData},${valStr}";
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }

    str valStr = if(currentIndex == index) replacement else current;
    newData = if(newData.isEmpty()) valStr else "${newData},${valStr}";

    this.data = newData;
}

warp int IntList.removeAt(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("IntList.removeAt: index out of bounds: ${index} (length: ${this.length})");
    }

    str newData = "";
    int currentIndex = 0;
    str current = "";
    int removed = 0;

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            if(currentIndex == index) {
                removed = cast::toInt(current);
            } else {
                newData = if(newData.isEmpty()) current else "${newData},${current}";
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }

    if(currentIndex == index) {
        removed = cast::toInt(current);
    } else {
        newData = if(newData.isEmpty()) current else "${newData},${current}";
    }

    this.data = newData;
    this.length--;
    return removed;
}

warp int IntList.indexOf(int item) {
    int currentIndex = 0;
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            if(cast::toInt(current) == item) {
                return currentIndex;
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }

    if(current.isNotEmpty() && cast::toInt(current) == item) {
        return currentIndex;
    }

    return -1;
}

warp bool IntList.remove(int item) {
    int idx = this.indexOf(item);
    if(idx == -1) {
        return false;
    }
    this.removeAt(idx);
    return true;
}

warp bool IntList.contains(int item) {
    return this.indexOf(item) != -1;
}

warp int? IntList.firstOrNull() {
    return null if(this.isEmpty());
    return this[0];
}

warp int? IntList.lastOrNull() {
    return null if(this.isEmpty());
    return this[this.length - 1];
}

warp IntList IntList.map((int) -> int action) {
    IntList out = newIntList();
    this.forEach((int item) -> {
        out.add(action(item));
    });
    return out;
}