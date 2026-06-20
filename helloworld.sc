import std as lib;

struct User(str name, float score);

void main() {
    float calculation = -User("Alice", 99.9) + !true;

    str hello = "Hello, World!";
    User user = User("Sigma", 999.0);

    lib::say("${hello} My name is ${user.name}");
}