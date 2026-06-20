import std;

struct User(str name, float score);

void main() {
    str hello = "Hello, World!";
    User user = User("Sigma", 999.0);

    std::say("${hello} My name is ${user.name}");
}