# Primitives

Scratcher has 7 primitive types:

- void
- int
- str
- char
- float
- bool
- auto

All of these primitive types are used for one simple thing:

### void
Void is used to indicate that a function does not return a value, it can only be used as a function's return:
```
void update() {
    ...
}
```
Update does not return a value, it just updates and keeps going.

### int
Int is used to indicate an `integer`, with no decimal places:

```
int a() {
    return 5;
}
```
The 5 can be any integer, negative or positive.

### float
Float is used to indicate a `floating point` number.

Float is a special type, you can pass any `int` into a float argument.

```
float pi = 3.14;
float pi = 3;
```

### str
The str type is just an abbreviation of string. Any string works.

```
str a = "hello world!";
```

### bool
The bool type is a simple boolean, it can either be `true` or `false`

```
bool a = false;
bool b = true;
```

### char
The char type represents a singular character.

```
char myChar = 'b';
char a = '5';
```

### auto
The auto type is a special type that can only be used in variables. The auto type is automatically changed at compile time to what the value of the variable is:

```
auto a = "hello";
```
"a" is a `str` because its value is a string.
```
auto b = 67;
```
"b" is an `int` because its value is an integer.

## Next up

- Make your [own types](top_level_objects.md)!