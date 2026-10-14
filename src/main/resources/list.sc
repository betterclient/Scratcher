import array::*;
import except;
import math::*;

struct List<T>(
    private int length,
    private T?[] ptr
);

warp <T> List<T> newList() {
    return List(0, arrayOfNulls(1));
}

warp <T> int List<T>.length() {
    return this.length;
}

warp <T> void List<T>.add(T item) {
    if(this.length == this.ptr.length()) {
        reserve(
            if(this.ptr.length() == 0) {
                2
            } else {
                this.length * 2
            }
        );
    }

    this.ptr[this.length] = item;
    this.length++;
}

warp <T> void List<T>.reserve(int newCapacity) {
    if(newCapacity < this.length) {
        return;
    }

    if(newCapacity <= this.ptr.length()) {
        return;
    }

    auto newArray = arrayOfNulls(newCapacity);
    //copy over
    repeat(this.length) { index ->
        newArray[index] = this.ptr[index];
    }

    this.ptr = newArray;
}

warp operator <T> T List<T>.get(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("List.get: index out of bounds: ${index} (length: ${this.length})");
    }

    return this.ptr[index];
}

warp operator <T> void List<T>.set(int index, T item) {
    if(index < 0 || index >= this.length) {
        except::panic("List.set: index out of bounds: ${index} (length: ${this.length})");
    }

    this.ptr[index] = item;
}

warp <T> void List<T>.clear() {
    this.length = 0;
    this.ptr = arrayOfNulls(1);
}

warp <T> T List<T>.removeAt(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("List.removeAt: index out of bounds: ${index} (length: ${this.length})");
    }

    auto removedItem = this.ptr[index];

    int i = index;
    repeat(this.length - index - 1) {
        this.ptr[i] = this.ptr[i + 1];
        i++;
    }

    this.length--;
    this.ptr[this.length] = null;
    return removedItem;
}

warp <T> bool List<T>.remove(T item) {
    int foundIndex = -1;

    repeat(this.length) { index ->
        if(foundIndex == -1 && this.ptr[index] === item) {
            foundIndex = index;
            break;
        }
    }

    return false if(foundIndex == -1);

    //shift
    int i = foundIndex;
    repeat(this.length - foundIndex - 1) {
        this.ptr[i] = this.ptr[i + 1];
        i++;
    }

    this.length--;
    this.ptr[this.length] = null;
    return true;
}

warp <T> void List<T>.fit() {
    return if(this.ptr.length() == this.length || this.length == 0);

    auto newArray = arrayOfNulls(this.length);
    repeat(this.length) { index ->
        newArray[index] = this.ptr[index];
    }
    this.ptr = newArray;
}

//extensions
warp <T> void List<T>.forEach((T) -> void action) {
    for(auto t in this) {
        action(t);
    }
}

warp <T> void List<T>.forEachIndexed((int, T) -> void action) {
    for(auto t in this) { index ->
        action(index, t);
    }
}

warp <T, R> List<R> List<T>.map((T) -> R action) {
    List<R> out = newList();
    out.reserve(this.length);
    for(auto t in this) {
        out.add(action(t));
    }
    return out;
}

warp <T, R> List<R> List<T>.map((int, T) -> R action) {
    List<R> out = newList();
    out.reserve(this.length);
    for(auto t in this) { index ->
        out.add(action(index, t));
    }
    return out;
}

warp <T> void List<T>.replaceAll((T) -> T action) {
    for(auto t in this) { index ->
        this[index] = action(t);
    }
}

warp <T> List<T> List<T>.filter((T) -> bool action) {
    List<T> out = newList();
    out.reserve(this.length);
    for(auto t in this) {
        if(action(t)) {
            out.add(t);
        }
    }
    return out;
}

warp <T> List<T> List<T>.filterNot((T) -> bool action) {
    return filter(T t -> !action(t));
}

warp <T> List<T> List<T?>.filterNotNull() {
    List<T> out = newList();
    out.reserve(this.length);
    for(auto t in this) {
        if(t != null) {
            out.add(t!!);
        }
    }
    return out;
}

warp <T> bool List<T>.any((T) -> bool action) {
    for(auto t in this) {
        return true if(action(t));
    }

    return false;
}

warp <T> bool List<T>.none((T) -> bool action) {
    return !any(action);
}

warp <T> int List<T>.count((T) -> bool action) {
    int amount = 0;
    for(auto t in this) {
        if(action(t)) {
            amount++;
        }
    }
    return amount;
}

warp <T> bool List<T>.all((T) -> bool action) {
    for(auto t in this) {
        return false if(!action(t));
    }

    return true;
}

warp <T> bool List<T>.contains(T item) {
    for(auto t in this) {
        return true if(t === item);
    }
    return false;
}

warp <T> int List<T>.indexOf(T item) {
    for(auto t in this) { index ->
        return index if(t === item);
    }
    return -1;
}

warp <T> T? List<T>.firstOrNull() {
    if(isEmpty()) {
        return null;
    }
    return this[0];
}

warp <T> T? List<T>.lastOrNull() {
    if(isEmpty()) {
        return null;
    }
    return this[length() - 1];
}

warp <T> T? List<T>.find((T) -> bool action) {
    for(auto t in this) {
        return t if(action(t));
    }
    return null;
}

warp <T> bool List<T>.isEmpty() {
    return length() == 0;
}

warp <T> bool List<T>.isNotEmpty() {
    return length() > 0;
}

warp <T, R> List<R> List<T>.flatMap((T) -> List<R> transform) {
    List<R> out = newList();
    for(auto t in this) {
        for(auto item in transform(t)) {
            out.add(item);
        }
    }
    return out;
}

warp <T> List<T> List<T>.take(int n) {
    List<T> out = newList();
    int count = 0;
    for(auto t in this) {
        return out if(count >= n);

        out.add(t);
        count++;
    }
    return out;
}

warp <T> List<T> List<T>.drop(int n) {
    List<T> out = newList();
    for(auto t in this) { index ->
        if(index >= n) {
            out.add(t);
        }
    }
    return out;
}

warp <T> void List<T>.addAll(List<T> other) {
    for(auto t in other) {
        this.add(t);
    }
}

warp <T> void List<T>.addAll(T[] other) {
    for(auto t in other) {
        this.add(t);
    }
}

warp operator <T> List<T> plus(List<T> first, List<T> second) {
    List<T> out = newList();
    out.reserve(first.length() + second.length());
    out.addAll(first);
    out.addAll(second);
    return out;
}

warp <T> T[] List<T>.toArray() {
    return arrayOf(this.length(), (int index) -> {
        return this[index];
    });
}

warp <T> List<T> T[].toList() {
    List<T> out = newList();
    out.reserve(this.length());
    out.addAll(this);
    return out;
}

warp <T> str List<T>.joinToString(str delimeter, (T) -> str toString) {
    return "" if(isEmpty());
    str out = toString(this[0]);
    int index = 1;
    repeat(this.length - 1) {
        out = "${out}${delimeter}${toString(this[index])}";
        index++;
    }
    return out;
}

warp <T> void List<T>.sort((T, T) -> int compare) {
    return if length() <= 1;

    this.sortInternal(0, length() - 1, compare);
}

private warp <T> void List<T>.sortInternal(int l, int h, (T, T) -> int compare) {
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