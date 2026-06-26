import looks;
import cast;
import sensing;
import mem;

struct User(int a, int b, Info? information);
struct Info(str name, int age);

Info myInfo = Info("hallo world", 67);

void main() {
    int b = 5;
    User u = User(5, cast::toIntOrDefault(sensing::ask("What should I print?"), 5), null);
    if(u.information != null) {
        print(u.information!!);
        mem::free(u.information!!);
    } else {
        print(myInfo);
    }
    mem::free(u);
}

Info gen() {
    return Info("Micheal", 55);
}

void print(Info info) {
    looks::say("${info.name} is ${info.age} years old!");
}

warp int a(bool a) {
    if(a) {
        return 5;
    } else {
        return 67;
    }
}

on GreenFlag {
    main();
}

on KeyPressed(W) {
    looks::say("W was pressed!");
}