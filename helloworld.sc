import std as lib;

struct User(str name, float score);

void main() {
    str hello = "Hello, World!";
    User user = User("Sigma", 999.0);

    lib::say("${hello} My name is ${user.name}");
}