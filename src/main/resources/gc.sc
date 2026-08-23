import gc_internal as self;
import utils;
import extensions;
import cast;

int gc_LastCollected = 0;
int gc_Epoch = 1;

on GreenFlag {
    while(true) {
        utils::wait(1); //collect every second
        gc_LastCollected = collect();
    }
}

warp int collect() {
    return 0 if(self::lengthOfRoots() == 0); //????

    //setup marker
    gc_Epoch++;

    int rootIndex = 1;
    repeat(self::lengthOfRoots()) {
        markRoot(cast::toIntOrDefault(self::getRoots(rootIndex), -1));
        rootIndex++;
    }
    markTopLevels();

    //mark the already freed items
    int freeIndex = 1;
    repeat(self::lengthOfFreeList()) {
        mark(cast::toIntOrDefault(self::getFreeList(freeIndex), -1));
        freeIndex++;
    }

    return sweep();
}

warp int sweep() {
    int freed = 0;
    int index = 1;
    int blockStart = -1;
    int blockSize = 0;

    repeat(self::getHeapSize()) {
        if(!isMarked(index)) {
            if (blockStart == -1) {
                blockStart = index;
            }
            blockSize++;
            freed++;
        } else {
            if (blockSize > 0) {
                self::freeHeapBlock(blockStart, blockSize);
                blockStart = -1;
                blockSize = 0;
            }
        }
        index++;
    }
    if (blockSize > 0) {
        self::freeHeapBlock(blockStart, blockSize);
    }
    return freed;
}

warp void markRoot(int addr) {
    return if !isValid(addr);

    str name = findName(addr);
    return if(name == "0");

    mark(addr - 1);

    int start = self::getFieldsStart(name);
    int count = self::getFieldsCount(name);
    int typeIndex = 0;
    repeat(count) {
        str type = self::getFieldType(start + typeIndex);
        markType(addr + typeIndex, type);
        typeIndex++;
    }
}

warp void markType(int addr, str type) {
    return if !isValid(addr);

    mark(addr);

    return if(type.contains("n"));
    return if(type == "p");

    if(type == "?") {
        int val = cast::toIntOrDefault(cast::toStr(self::getHeap(addr)), -1);
        return if(val <= 0);
        markRoot(val);
        return;
    }

    return if(cast::toStr(self::getHeap(addr)) == "null");

    if(type.contains("l")) {
        markList(self::getHeap(addr), type);
    } else {
        markStruct(self::getHeap(addr), type);
    }
}

warp void markList(int addr, str type) {
    return if !isValid(addr);
    int capacity = self::getListCapacity(addr);
    mark(addr - 1); //alloc name

    int hIndex = 0;
    repeat(self::getListHeaderSize()) {
        mark(addr + hIndex);
        hIndex++;
    }

    //substring(1, length)
    //^^^ remove first letter
    int index = 2;
    str out = "";
    repeat(type.length() - 1) {
        out = out.concat(type.charAt(index));
        index++;
    }

    //^ actual list type
    //now we gotta go to the dataPtr in the heap
    int data = self::getListDataPtr(addr);
    if(cast::toStr(data) != "null") {
        mark(data - 1);
    }
    index = 0;
    repeat(capacity) {
        //now recurse!!
        if(out != "p" && out != "0" && out.length() > 0) {
            markType(data + index, out);
        } else {
            mark(data + index);
        }
        index++;
    }
}

warp void markStruct(int addr, str type) {
    return if !isValid(addr);
    return if(self::isStack(type)); //stacks already mark themselves

    mark(addr - 1);

    int start = self::getFieldsStart(type);
    int count = self::getFieldsCount(type);
    int typeIndex = 0;
    repeat(count) {
        int actualAddr = addr + typeIndex;
        str fieldType = self::getFieldType(start + typeIndex);
        if(fieldType == "p" || fieldType == "0") {
            mark(actualAddr);
        } else {
            markType(actualAddr, fieldType);
        }
        typeIndex++;
    }
}

warp str findName(int addr) {
    return cast::toStr(self::getHeap(addr - 1));
}

warp bool isValid(int addr) {
    return false if(addr == -1);
    return false if(addr == 0);
    return false if(cast::toStr(addr) == "null");
    return false if(cast::toStr(addr) == "");
    return false if(isMarked(addr));

    return cast::toStr(addr) != "reserved";
}

warp bool isMarked(int addr) {
    return true if (addr <= 0);
    
    return self::getMarked(addr) == gc_Epoch;
}

warp void mark(int addr) {
    return if(addr <= 0);

    self::setMarked(addr, gc_Epoch);
}

warp void markTopLevels() {
    return if(!self::isReflectGC());

    str currentType = "";
    int index = 1;
    repeat(self::lengthOfReflect()) {
        str obj = self::getReflect(index);
        if(index % 2 == 0) {
            int addr = self::reflect(obj);
            if(currentType.contains("l")) {
                markList(addr, currentType);
            } else {
                markStruct(addr, currentType);
            }
        } else {
            currentType = obj;
        }

        index++;
    }
}