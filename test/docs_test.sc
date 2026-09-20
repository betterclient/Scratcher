import looks::say;
import list_pool::*;
import list::*;
import extensions;

on GreenFlag {
    PooledList a = allocate();
    a.add("67");

    say("${a[0]}");
}