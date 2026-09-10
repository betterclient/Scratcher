# Nullability and generics
- Scratcher provides compile time nullability and generic support.

## Nullability
By default, types in Scratcher cannot hold `null`. Make them able to hold `null` by adding the `?` after the type.

```
int? a = null;
```

- If a type is marked `nullable`, it cannot be used normally, it must be used like so.

### Unsafe ways to use nullables
- The non null assert `!!`:
```
int? a = ...;

int newA = a!!;
```
- The `!!` operator asserts that the value isn't null.
- But if the value turns out to be `null` anyway, the runtime will throw a `panic`.

### Safe ways to use nullables
- Just check if its null first
```
int? a = ...;
if(a != null) {
    int newA = a!!;
    
    use newA here
}
```

- Use the `?.` syntax if it's a struct
```
Plane? p = ...;

int? altitude = p?.altitude;
```
- This compiles directly to
```
int? altitude = if(p == null) null else p!!.altitude; 
```
- Which allows for safely traversing structs.

- Use the fallback operator `?:`
```
int a = ... ?: 5;
```
- This allows providing a fallback value if the initial given value is `null`.

## Generics
Generics allow creating functions/structs/sealed enums that may be used with any type.

- The best example for generics is a `Result` sealed enum:
```
sealed enum Result<T, E> {
    Success(T value),
    Failure(E error)
}
```
This makes it so the `Success` and `Failure` values can be anything.

```
Result<int, str> a = Result.Success(5);

Result<int, str> a = Result.Failure("error happened!");
```

- Another good example is a `Box` struct:
```
struct Box<T>(T value);
```
- This box struct can hold anything.
```
auto myBox = Box(5);
```
- The type of myBox can be inferred, but the same can't be said for the `Result` case.
- Generic functions can also be declared
```
<T> T identity(T value) {
    return value;
}
```
- This function returns the given value directly.

## Next up
- Learn about control flow, receiver functions and loops by making a [linked list](linked_list.md)!