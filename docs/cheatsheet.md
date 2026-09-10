# Cheatsheet

## Standard library

### `looks`
- `void say(str message)`
- `void say(str message, float seconds)`

### `sensing`
- `str ask(str message)`
- `float getDistanceToMouse()`
- `int getMouseX()`
- `int getMouseY()`
- `float getTimer()`
- `void resetTimer()`
- `str getUsername()`
- `float getDaysSince2000()`
- `bool isOnline()`
- `bool isMousePressed()`
- `bool isKeyPressed(str key)`

### `math`
- `int abs(int number)`
- `float abs(float number)`
- `float floor(float number)`
- `float ceil(float number)`
- `float sqrt(float number)`
- `float sin(float number)`
- `float cos(float number)`
- `float tan(float number)`
- `float asin(float number)`
- `float acos(float number)`
- `float atan(float number)`
- `float ln(float number)`
- `float log(float number)`
- `float exp(float number)`
- `float pow10(float number)`
- `float pow(float base, float exponent)`
- `int round(float value)`

### `except`
- `void panic(str message)`

### `cast`
- `float toFloatOrDefault(str value, float fallback)`
- `float toFloat(str value)`
- `int toIntOrDefault(str value, int fallback)`
- `int toInt(str value)`
- `bool toBoolOrDefault(str value, bool fallback)`
- `bool toBool(str value)`
- `char toChar(str value)`
- `char toCharOrDefault(str value, char fallback)`
- `str toStr(float value)`
- `str toStr(bool value)`
- `str toStr(char value)`

### `calendar`
- `int getDayOfWeek()`
- `int getYear()`
- `int getMonth()`
- `int getDayOfMonth()`
- `int getHour()`
- `int getMinute()`
- `int getSecond()`

### `pen`
- `void down()`
- `void up()`
- `void setColor(float color)`
- `void setSize(float size)`
- `void eraseAll()`

### `motion`
- `float getX()`
- `float getY()`
- `float getDirection()`
- `void gotoXY(float x, float y)`
- `void gotoX(float x)`
- `void gotoY(float y)`
- `void gotoMouse()`
- `void gotoRandom()`
- `void glideToXY(float x, float y, float secs)`
- `void glideToX(float x, float secs)`
- `void glideToY(float y, float secs)`
- `void glideToMouse(float secs)`
- `void glideToRandom(float secs)`
- `void turnLeft(float degrees)`
- `void turnRight(float degrees)`
- `void setTurnStyleToLeftRight()`
- `void setTurnStyleToDontRotate()`
- `void setTurnStyleToAllAround()`

### `utils`
- `float random(float from, float to)`
- `int random(int from, int to)`
- `void wait(float seconds)`

### `list`
**Note**: Lists are 0-indexed.
- `<T> List<T> newList()`
- `<T> int List<T>.length()`
- `<T> void List<T>.add(T item)`
- `<T> void List<T>.reserve(int newCapacity)`
- `operator <T> T List<T>.get(int index)`
- `operator <T> void List<T>.set(int index, T item)`
- `<T> void List<T>.clear()`
- `<T> T List<T>.removeAt(int index)`
- `<T> bool List<T>.remove(T item)`
- `<T> void List<T>.forEach((T) -> void action)`
- `<T> void List<T>.forEachIndexed((int, T) -> void action)`
- `<T, R> List<R> List<T>.map((T) -> R action)`
- `<T, R> List<R> List<T>.map((int, T) -> R action)`
- `<T> void List<T>.replaceAll((T) -> T action)`
- `<T> List<T> List<T>.filter((T) -> bool action)`
- `<T> List<T> List<T>.filterNot((T) -> bool action)`
- `<T> List<T> List<T?>.filterNotNull()`
- `<T> bool List<T>.any((T) -> bool action)`
- `<T> bool List<T>.none((T) -> bool action)`
- `<T> int List<T>.count((T) -> bool action)`
- `<T> bool List<T>.all((T) -> bool action)`
- `<T> bool List<T>.contains(T item)`
- `<T> int List<T>.indexOf(T item)`
- `<T> T? List<T>.firstOrNull()`
- `<T> T? List<T>.lastOrNull()`
- `<T> T? List<T>.find((T) -> bool action)`
- `<T> bool List<T>.isEmpty()`
- `<T> bool List<T>.isNotEmpty()`
- `<T, R> List<R> List<T>.flatMap((T) -> List<R> transform)`
- `<T> List<T> List<T>.take(int n)`
- `<T> List<T> List<T>.drop(int n)`
- `<T> void List<T>.addAll(List<T> other)`
- `<T> void List<T>.addAll(T[] other)`
- `operator <T> List<T> plus(List<T> first, List<T> second)`
- `<T> T[] List<T>.toArray()`
- `<T> List<T> T[].toList()`

### `array`
**Note**: Arrays are 0-indexed.
- `<T> T?[] arrayOfNulls(int size)`
- `<T> T[] arrayOf(int size, (int) -> T init)`
- `<T> T[] arrayOf(T first)`
- `<T> T[] arrayOf(T first, T second)`
- `<T> int T[].length()`
- `operator <T> T T[].get(int index)`
- `operator <T> void T[].set(int index, T item)`

### `extensions`
- `<T, R> R T.let((T) -> R action)`
- `<T> T T.also((T) -> void action)`
- `bool str.isEmpty()`
- `bool str.isNotEmpty()`
- `str str.concat(str right)`
- `int str.length()`
- `char str.charAt(int index)`
- `bool str.contains(str other)`
- `char[] str.toCharArray()`
- `str str.substring(int from, int to)`
- `str str.substringInclusive(int fromInclusive, int toInclusive)`
- `bool str.startsWith(str prefix)`
- `bool str.endsWith(str suffix)`
- `int str.indexOf(char target)`
- `<T> void T[].copyTo(T[] other)`
- `<T, R> R[] T[].map((T) -> R action)`
- `<T> void T[].forEach((T) -> void action)`

### `triangle`
- `void fill(float x1, float y1, float x2, float y2, float x3, float y3, float color, float resolution)`
- `void outline(float x1, float y1, float x2, float y2, float x3, float y3, float color, float resolution)`
- `float rgb(float r, float g, float b)`

## Syntax

### Top level objects
- Structs:
```
struct Person(int x, int y, int z, float size);

//usage
Person p = Person(5, 15, 25, 2.5);
p.x = 55;
```
- Enums:
```
enum Mode(OFF, PARTIAL, FULL);

//usage
Mode m = Mode.OFF;
```
- Sealed enums:
```
sealed enum Result<T, E> {
    Success(T value),
    Failure(E error)
}

//usage
Result<int, str> a = Result.Failure("hello world!");

a is Result.Success
(a as Result.Failure).error
```

### Functions

- Simple
```
int multiply(int num) {
    return num * 3;
}
```

- Modifiers:
- - `warp`: "Run without screen refresh"
- - `private`: Only accessible from current file
- - `export`: Allow it to be called from native scratch
- - `operator`: Use as operator


- Operator function examples:
```
warp operator <T> List<T> plus(List<T> first, List<T> second) {
    List<T> out = newList();
    out.addAll(first);
    out.addAll(second);
    return out;
}

warp operator <T> T List<T>.get(int index) {
    return this.ptr[index];
}
```
- - Allowed operator functions:
- - `+`, `-`, `/`, `*`, `%`, `[]`, `[] = ..`

### Control flow

- If statement
```
if(condition) {
    ...
}

if(condition) {
    ...
} else {
    ...
}

if(condition) {
    ...
} else if(condition2) {
    ...
} else {
    ...
}
``` 
- If expression
```
auto v = if(condition) {
    ...
    value
} else {
    ...
    value
}
//else if also works here
```
- When statement
```
when(subject) {
    value1 -> {}
    value2 -> {}
    value3 -> {}
    else -> {}
}

when {
    condition0 -> {}
    condition1 -> {}
    condition2 -> {}
    else -> {}
}
```
- When expression
```
auto v = when(subject) {
    value1 -> { ... value }
    value2 -> value
    else -> value
}
```

- When pattern matching with sealed enums
```
when(result) {
    Result.Failure fail -> {
    
    }
    Result.Success success -> {
    
    }    
}
```

### Return

- `return value`
- `return value if(condition)`
- `return value if condition`

### Loops

- Repeat
```
repeat(amount) {
    ...
}
```

- While
```
while(condition) {
    ...
}
```

- List/Array loop
```
for(auto item in list) { ... }
for(auto item in array) { ... } 
```

### Nullability
- `T?`: Nullable type
- `?.`: Safe navigation(structs)
- `?:`: Fallback if null
- `!!`: Non-null assertion (triggers runtime panic if null)

### Events
- on GreenFlag { ... }
- on KeyPressed("key") { ... }

### Imports

- `import module;`: Import stdlib module
- `import module::*;`: Import everything from stdlib module flattened
- `import module::some;`: Import some things from stdlib module flattened
- `import module::{some, other};`: Import a list of things.

Or use `"file.sc"` to import from files instead of stdlib.

### Types
Primitive: void, int, str, char, float, bool, auto
- Auto used to automatically infer types, will throw an error if not possible.

- Arrays: Type[]
- Generics: Type<Type, Type>
- Function reference: (args) -> return

### Lambdas/Function references
- Function references:
```
void hi() { ... }
void funcWithReferenceInput(() -> void action) { ... }

funcWithReferenceInput(&hi);
```
- Lambda:
```
int a = 0;
funcWithReferenceInput(() -> {
    ... 
    a++;
});
//a is 1 here
```
Lambdas support variable captures and capture mutation. Any local variable outside a lambda can be affected by a lambda.

## Other stuff
- String interpolation: `"Hi: ${1 + 1}"`
- If **statement** does not support braceless blocks.
- No smart casting.
- No short-circuiting.
- No `continue`, `break`.