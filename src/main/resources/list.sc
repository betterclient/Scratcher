import array;
import except;

struct List<T>(
    int length,
    T[] ptr
);

warp <T> List<T> newList() {
    return List(0, Array(T, 1));
}

warp <T> int List<T>.length() {
    return this.length;
}

warp <T> void List<T>.add(T item) {
    if(this.length == array::length(this.ptr)) {
        reserve(
            if(array::length(this.ptr) == 0) {
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
        except::panic("List.reserve: newCapacity < length, ${newCapacity} < ${this.length}");
    }

    auto newArray = Array(T, newCapacity);
    //copy over
    int index = 0;
    repeat(this.length) {
        newArray[index] = this.ptr[index];
        index++;
    }

    this.ptr = newArray;
}

warp <T> T List<T>.itemAt(int index) {
    if(index < 0 || index >= this.length) {
        except::panic("List.itemAt: index out of bounds: ${index} (length: ${this.length})");
    }

    return this.ptr[index];
}

warp <T> void List<T>.replace(int index, T item) {
    if(index < 0 || index >= this.length) {
        except::panic("List.replace: index out of bounds: ${index} (length: ${this.length})");
    }

    this.ptr[index] = item;
}

warp <T> void List<T>.clear() {
    this.length = 0;
    this.ptr = Array(T, 1);
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
    return removedItem;
}

warp <T> bool List<T>.remove(T item) {
    int index = 0;
    int foundIndex = -1;

    repeat(this.length) {
        if(foundIndex == -1 && this.ptr[index] == item) {
            foundIndex = index;
        }
        index++;
    }

    if(foundIndex == -1) {
        return false;
    }

    //shift
    int i = foundIndex;
    repeat(this.length - foundIndex - 1) {
        this.ptr[i] = this.ptr[i + 1];
        i++;
    }

    this.length--;
    return true;
}

//extensions
warp <T> void List<T>.forEach((T) -> void action) {
    for(auto t in this) {
        action(t);
    }
}

warp <T> void List<T>.forEachIndexed((int, T) -> void action) {
    int index = 0;
    for(auto t in this) {
        action(index, t);
        index++;
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
    int index = 0;
    for(auto t in this) {
        out.add(action(index, t));
        index++;
    }
    return out;
}

warp <T> void List<T>.replaceAll((T) -> T action) {
    int index = 0;
    for(auto t in this) {
        this[index] = action(t);

        index++;
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
        return true if(t == item);
    }
    return false;
}

warp <T> int List<T>.indexOf(T item) {
    int index = 0;
    for(auto t in this) {
        return index if(t == item);
        index++;
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
    int index = 0;
    for(auto t in this) {
        if(index >= n) {
            out.add(t);
        }
        index++;
    }
    return out;
}