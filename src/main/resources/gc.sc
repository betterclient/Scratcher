import gc_internal as self;
import list;
import utils;

on GreenFlag {
    while(true) {
        collect();
        utils::wait(1); //collect every second, TODO: expose collect as a function... maybe in utils?
    }
}

//marking as "warp" causes stop the world
warp void collect() {
    //find "roots"
    int index = 0;
    int[] roots = List(int);
    repeat(self::lengthOfAllocAddressList()) {
        str name = self::getAllocNameList(index);
        int address = self::getAllocAddressList(index);
        if(self::isStack(name)) {
            list::add(roots, address);
        }

        index = index + 1;
    }
}