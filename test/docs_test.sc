import looks::say;
import list_pool::*;
import list::*;
import extensions;

on GreenFlag {
    List<str> myList = newList();
    myList.let((List<str> list) -> {
        repeat(12013490123) {
            list.add("67");
        }
    });

    PooledList a = allocate();
    a.add("67");

    say("${a[0]}");

    a.free();
}