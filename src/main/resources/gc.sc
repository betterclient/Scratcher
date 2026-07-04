import gc_internal as self;
import list;
import utils;
import string;
import cast;

on GreenFlag {
    while(true) {
        utils::wait(1); //collect every second
        collect();
    }
}

warp int collect() {
    self::clearMarked();
    return 0 if(self::lengthOfRoots() == 0); //????

    int rootIndex = 1;
    repeat(self::lengthOfRoots()) {
        markRoot(cast::toIntOrDefault(self::getRoots(rootIndex), -1));
        rootIndex = rootIndex + 1;
    }

    //mark the already freed items
    int freeIndex = 1;
    repeat(self::lengthOfFreeList()) {
        self::addMarked(cast::toIntOrDefault(self::getFreeList(freeIndex), -1));
        freeIndex = freeIndex + 1;
    }

    return sweep();
}

warp int sweep() {
    int freed = 0;
    int index = 1;
    repeat(self::getHeapSize()) {
        if(!self::isMarked(index)) {
            self::freeHeap(index);
            freed = freed + 1;
        }
        index = index + 1;
    }
    return freed;
}

warp void markRoot(int addr) {
    return if(addr == -1);
    return if(cast::toStr(addr) == "");
    return if(cast::toStr(addr) == "reserved");

    str name = findName(addr);
    return if(name == "0");

    int start = self::getFieldsStart(name);
    int count = self::getFieldsCount(name);
    int typeIndex = 0;
    repeat(count) {
        str type = self::getFieldType(start + typeIndex);
        markType(addr + typeIndex, type);
        typeIndex = typeIndex + 1;
    }
    //self::freeStrArray(types); freeing this breaks everything????????????
}

warp void markType(int addr, str type) {
    return if !isValid(addr);

    self::addMarked(addr);

    return if(string::contains(type, "n"));
    return if(type == "p");

    return if(self::getHeap(addr) == -1);

    if(string::contains(type, "l")) {
        markList(self::getHeap(addr), type);
    } else {
        markStruct(self::getHeap(addr), type);
    }
}

warp void markList(int addr, str type) {
    return if !isValid(addr);
    int capacity = self::getHeap(addr + 1);
    self::addMarked(addr);  //length
    self::addMarked(addr + 1); //capacity
    self::addMarked(addr + 2); //dataPtr
    self::addMarked(addr + 3); //name

    //substring(1, length)
    //^^^ remove first letter
    int index = 2;
    str out = "";
    repeat(string::length(type) - 1) {
        str char = string::charAt(type, index);
        out = string::concat(out, char);
        index = index + 1;
    }

    //^ actual list type
    //now we gotta go to the dataPtr in the heap
    int data = self::getHeap(addr + 2);
    index = 0;
    repeat(capacity) {
        //now recurse!!
        if(out != "p" && out != "0" && string::length(out) > 0) {
            markType(data + index, out);
        } else {
            self::addMarked(data + index);
        }
        index = index + 1;
    }
}

warp void markStruct(int addr, str type) {
    return if !isValid(addr);
    return if(self::isStack(type)); //stacks already mark themselves

    int start = self::getFieldsStart(type);
    int count = self::getFieldsCount(type);
    int typeIndex = 0;
    repeat(count) {
        int actualAddr = addr + typeIndex;
        str fieldType = self::getFieldType(start + typeIndex);
        if(fieldType == "p" || fieldType == "0") {
            self::addMarked(actualAddr);
        } else {
            markType(actualAddr, fieldType);
        }
        typeIndex = typeIndex + 1;
    }
}

warp str findName(int addr) {
    int index = 1;
    repeat(self::lengthOfAllocAddressList()) {
        if(self::getAllocAddressList(index) == addr) {
            return self::getAllocNameList(index);
        }
        index = index + 1;
    }
    return "0";
}

warp bool isValid(int addr) {
    return false if(addr == -1);
    return false if(cast::toStr(addr) == "");
    return false if(self::isMarked(addr));

    return cast::toStr(addr) != "reserved";
}