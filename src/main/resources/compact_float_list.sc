import extensions;
import list::*;
import array::*;
import cast;
import except;

struct FloatList(
    private str data,
    private int length
);

warp FloatList newFloatList() {
    return FloatList("", 0);
}

warp FloatList List<float>.toCompact() {
    return FloatList(this.joinToString(",", (float obj) -> {
        return cast::toStr(obj);
    }), this.length());
}

warp int FloatList.length() {
    return this.length;
}

warp bool FloatList.isEmpty() {
    return this.length == 0;
}

warp bool FloatList.isNotEmpty() {
    return this.length > 0;
}

warp void FloatList.add(float item) {
    if(this.length == 0) {
        this.data = "${item}";
    } else {
        this.data = "${this.data},${item}";
    }
    this.length++;
}

warp void FloatList.forEach((float) -> void action) {
    return if(this.length == 0);
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            action(cast::toFloat(current));
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    if(current.isNotEmpty()) {
        action(cast::toFloat(current));
    }
}

warp List<float> FloatList.fromCompact() {
    List<float> out = newList();
    this.forEach((float item) -> {
        out.add(item);
    });
    return out;
}

warp operator float FloatList.get(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("FloatList.get: index out of bounds: ${index} (length: ${this.length})");
    }

    int currentIndex = 0;
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            if(currentIndex == index) {
                return cast::toFloat(current);
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }

    return cast::toFloat(current);
}

warp operator void FloatList.set(int index, float item) {
    if(index < 0 || index >= this.length) {
        except::panic("FloatList.set: index out of bounds: ${index} (length: ${this.length})");
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

warp float FloatList.removeAt(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("FloatList.removeAt: index out of bounds: ${index} (length: ${this.length})");
    }

    str newData = "";
    int currentIndex = 0;
    str current = "";
    float removed = 0;

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            if(currentIndex == index) {
                removed = cast::toFloat(current);
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
        removed = cast::toFloat(current);
    } else {
        newData = if(newData.isEmpty()) current else "${newData},${current}";
    }

    this.data = newData;
    this.length--;
    return removed;
}

warp int FloatList.indexOf(float item) {
    int currentIndex = 0;
    str current = "";

    for(auto c in this.data.toCharArray()) {
        if(c == ',') {
            if(cast::toFloat(current) == item) {
                return currentIndex;
            }
            currentIndex++;
            current = "";
        } else {
            current = "${current}${c}";
        }
    }
    
    if(current.isNotEmpty() && cast::toFloat(current) == item) {
        return currentIndex;
    }

    return -1;
}

warp bool FloatList.remove(float item) {
    int idx = this.indexOf(item);
    if(idx == -1) {
        return false;
    }
    this.removeAt(idx);
    return true;
}

warp bool FloatList.contains(float item) {
    return this.indexOf(item) != -1;
}

warp float? FloatList.firstOrNull() {
    return null if(this.isEmpty());
    return this[0];
}

warp float? FloatList.lastOrNull() {
    return null if(this.isEmpty());
    return this[this.length - 1];
}

warp FloatList FloatList.map((float) -> float action) {
    FloatList out = newFloatList();
    this.forEach((float item) -> {
        out.add(action(item));
    });
    return out;
}