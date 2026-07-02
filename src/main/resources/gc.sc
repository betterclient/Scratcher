import gc_internal as self;
import list;
import utils;
import looks;
import string;
import cast;

on GreenFlag {
    while(true) {
        utils::wait(1); //collect every second, TODO: expose collect as a function... maybe in utils?
        collect();
    }
}

warp void collect() {
    self::clearMarked();
    int[] roots = findRoots();
    return if(list::length(roots) == 0); //????

    for(int a in roots) {
        markRoot(a);
    }

    //mark the already freed items
    int freeIndex = 1;
    repeat(self::lengthOfFreeList()) {
        self::addMarked(cast::toIntOrDefault(self::getFreeList(freeIndex), -1));
        freeIndex = freeIndex + 1;
    }

    sweep();
}

warp void sweep() {
    int freed = 0;
    int index = 1;
    repeat(self::getHeapSize()) {
        if(!self::isMarked(index)) {
            self::freeHeap(index);
            freed = freed + 1;
        }
        index = index + 1;
    }
    looks::say("GC: Freed ${freed} objects!");
}

warp void markRoot(int addr) {
    return if(addr == -1);
    return if(cast::toStr(addr) == "");
    return if(cast::toStr(addr) == "reserved");

    //self::addMarked(addr);

    str name = findName(addr);
    str insideTypes = self::getInternalNames(name);
    str[] types = split(insideTypes, "-");
    int typeIndex = 0;
    for(str type in types) {
         markType(addr + typeIndex, type);
         typeIndex = typeIndex + 1;
    }
}

warp void markType(int addr, str type) {
    return if !isValid(addr);
    self::addMarked(addr);
    return if(string::contains(type, "n")); //markList already marks the entire dataPtr

    if(type == "p") { //primitive
        self::addMarked(addr);
        return;
    }

    return if(self::getHeap(addr) == -1);

    if(string::contains(type, "l")) { //list
        markList(self::getHeap(addr), type);
    } else { //struct
        markStruct(self::getHeap(addr), type);
    }
}

warp void markList(int addr, str type) {
    return if !isValid(addr);
    int capacity = self::getHeap(addr + 1); //store capacity
    self::addMarked(addr); //length
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

    str internalTypes = self::getInternalNames(type);
    str[] types = split(internalTypes, "-");
    int typeIndex = 0;
    for(str type in types) {
        int actualAddr = addr + typeIndex;
        if(type == "p" || type == "0") {
            self::addMarked(actualAddr);
        } else {
            markType(actualAddr, type);
        }

        typeIndex = typeIndex + 1;
    }
}

warp str findName(int addr) {
    int index = 1;
    repeat(self::lengthOfAllocAddressList()) {
        str name = self::getAllocNameList(index);
        int address = self::getAllocAddressList(index);
        if(address == addr) {
            return name;
        }

        index = index + 1;
    }
    return "0";
}

warp int[] findRoots() {
    int index = 1;
    int[] roots = List(int);
    repeat(self::lengthOfAllocAddressList()) {
        str name = self::getAllocNameList(index);
        int address = self::getAllocAddressList(index);
        if(!(string::contains(name, "l") || string::contains(name, "nl"))) {
            if(self::isStack(name)) {
                list::add(roots, address);
            }
        }

        index = index + 1;
    }
    return roots;
}

warp str[] split(str input, str delimiter) {
    str current = "";
    int index = 1;
    str[] out = List(str);
    repeat(string::length(input)) {
        str char = string::charAt(input, index);
        if(char == delimiter) {
            list::add(out, current);
            current = "";
        } else {
            current = string::concat(current, char);
        }
        index = index + 1;
    }
    list::add(out, current);
    return out;
}

warp bool isValid(int addr) {
    return false if(addr == -1);
    return false if(cast::toStr(addr) == "");
    return false if(self::isMarked(addr));

    return cast::toStr(addr) != "reserved";
}