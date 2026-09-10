# Make your own types

### 1: Enums
- Enums are simple objects for storing a fixed collection of constants

You can define an enum by using the `enum` keyword
```
enum Direction(WEST, SOUTH, NORTH, EAST);
```
Inside your code, you can use the values like so:
```
Direction a = Direction.WEST;

if(a == Direction.NORTH) {
    ...
}

when(a) {
    Direction.NORTH -> ...
    Direction.WEST -> ...
    Direction.SOUTH -> ...
    Direction.EAST -> ...
}
```
**Note**: When using the `when` expression with enums, you will need to provide code for all values or use an `else` block.

### 2: Structs
- Structs contain objects

You can define a struct by using the `struct` keyword
```
struct Plane(Direction facingDirection, int altitude);
```

- Structs are passed by reference.
- Structs can be created with this syntax:
```
Plane p = Plane(Direction.WEST, 155);
```
- This creates a new instance of `Plane`, with `facingDirection` set to `west` and `altitude` set to 155.
- The arguments must be passed at the correct order.
- Objects inside structs are accessible by using the dot syntax:
```
Plane p = ...;
p.facingDirection = Direction.NORTH;
p.facingDirection
```

### 3: Sealed Enums
- Sealed enums are special kind of tagged unions.
- They behave a lot like both enums and structs
- They can be defined by using the `sealed enum` syntax
```
sealed enum Result {
    Success(str value),
    Failure
}
```
- Sealed enums can hold either normal enums (parameterless) or structs
- They can be created just like structs:
```
Result a = Result.Success("hi!");
Result b = Result.Failure;
```
- They can be checked using the `is` and `as` keywords
```
Result a = ...;
if(a is Result.Success) {
    Result.Success b = a as Result.Success;
}
```
- Or you can pattern match them using the `when` expression:
```
when(...) {
    Result.Failure -> {
        ...
    }
    Result.Success out -> {
        say("Value is ${out.value}");
    }
}
```
- In the `Result.Success` case, the Success instance is automatically converted and stored in the `out` variable.

## Next up:
- Learn about [nullability and generics](nullability_and_generics.md).