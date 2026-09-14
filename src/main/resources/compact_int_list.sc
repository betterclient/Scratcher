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
    return IntList(this.joinToString("F", (int obj) -> {
        return cast::toStr(obj);
    }), this.length());
}

warp List<int> IntList.fromCompact() {
    List<int> out = newList();
    str current = "";
    for(auto c in this.data.toCharArray()) {
        if(c == 'F') {
            out.add(cast::toInt(current));
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    if(current.isNotEmpty()) {
        out.add(cast::toInt(current));
    }

    return out;
}

warp void IntList.add(int item) {
    if(this.length == 0) {
        this.data = "${item}";
    } else {
        this.data = "${this.data}F${item}";
    }
    this.length++;
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
        if(c == 'F') {
            if(currentIndex == index) {
                removed = cast::toInt(current);
            } else {
                newData = if(newData.isEmpty()) current else "${newData}F${current}";
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    if(current.isNotEmpty()) {
        if(currentIndex == index) {
            removed = cast::toInt(current);
        } else {
            newData = if(newData.isEmpty()) current else "${newData}F${current}";
        }
    }

    this.data = newData;
    this.length--;
    return removed;
}

warp bool IntList.remove(int item) {
    int idx = this.indexOf(item);
    if(idx == -1) {
        return false;
    }
    this.removeAt(idx);
    return true;
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

warp operator int IntList.get(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("IntList.get: index out of bounds: ${index} (length: ${this.length})");
    }

    int currentIndex = 0;
    str current = "";
    int result = 0;

    for(auto c in this.data.toCharArray()) {
        if(c == 'F') {
            if(currentIndex == index) {
                result = cast::toInt(current);
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    if(current.isNotEmpty()) {
        if(currentIndex == index) {
            result = cast::toInt(current);
        }
    }

    return result;
}

warp operator void IntList.set(int index, int item) {
    if(index < 0 || index >= this.length) {
        except::panic("IntList.set: index out of bounds: ${index} (length: ${this.length})");
    }

    str newData = "";
    int currentIndex = 0;
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == 'F') {
            auto val = if(currentIndex == index) item else cast::toInt(current);
            newData = if(newData.isEmpty()) "${val}" else "${newData}F${val}";
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    if(current.isNotEmpty()) {
        auto val = if(currentIndex == index) item else cast::toInt(current);
        newData = if(newData.isEmpty()) "${val}" else "${newData}F${val}";
    }

    this.data = newData;
}

warp int IntList.indexOf(int item) {
    int currentIndex = 0;
    int foundIndex = -1;
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == 'F') {
            if(foundIndex == -1 && cast::toInt(current) == item) {
                foundIndex = currentIndex;
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    if(current.isNotEmpty()) {
        if(foundIndex == -1 && cast::toInt(current) == item) {
            foundIndex = currentIndex;
        }
    }

    return foundIndex;
}

warp bool IntList.contains(int item) {
    return this.indexOf(item) != -1;
}

warp int? IntList.firstOrNull() {
    if(this.isEmpty()) {
        return null;
    }
    return this[0];
}

warp int? IntList.lastOrNull() {
    if(this.isEmpty()) {
        return null;
    }
    return this[this.length - 1];
}

warp void IntList.forEach((int) -> void action) {
    return if(this.length == 0);
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == 'F') {
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

warp IntList IntList.map((int) -> int action) {
    IntList out = newIntList();
    this.forEach((int item) -> {
        out.add(action(item));
    });
    return out;
}
