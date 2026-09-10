# Native lists and arrays
Scratcher comes with arrays and array lists as standard library modules.

## Arrays
- Arrays are static collections that can't change size.
- You can use them by importing the `array` library
```
import array::*;
```
- Create new arrays:
```
int[] myArray = arrayOf(size, (int index) -> { ... });
```
Notice that you need to provide values for every index using the initializer, if you don't wanna do this, use `arrayOfNulls`
```
int?[] myArray = arrayOfNulls(size);
```
If you already have your objects:
```
int[] myArray = arrayOf(1, 2);
```
**Note**: This only works upto 2 elements.
- Loop through every object in an array:
```
for(auto object in myArray) {
    ...
}
```
- Use indexing operators
```
myArray[0] = ...;
say("${myArray[1]}");
```
- Get its length
```
myArray.length()
```

## Lists
- Lists are dynamic collections that use arrays under the hood. They can store any number of elements and be modified at any time.
- You can use them by importing the `list` library.
```
import list::*;
```
- Create new lists
```
List<int> myList = newList();
```
Or convert an array to a list:
```
List<int> myList = myArray.toList();
```
- Add new elements
```
myList.add(5);
```
- Remove elements
```
myList.remove(5); //remove the literal "5"
myList.removeAt(5); //remove index 5
```
- Loop through the list
```
for(auto item in myList) {
    ...
}

myList.forEach((int item) -> {
    ...
});
```
- Use the functional helpers to expressively make new lists
```
auto myNewList = myList
    .map(
        (int item) -> item * 2
    )
    .filter(
        (int item) -> item > 2
    )
    .take(5)
    .flatMap(
        (int item) -> arrayOf(5, (int index) -> index * 5 + item).toList()
    );
```
- Use indexing operators
```
myList[0] = ...;
say("${myList[1]}");
```
- Concatenate 2 lists:
```
auto addedTogether = list1 + list2;
```
- Convert back to arrays
```
int[] myNewArray = myList.toArray();
```

## Exercises
- Take an array of 10 numbers, convert it to a `List`, filter out all odd numbers, and double the remaining ones.
- Learn about [function modifiers](function_modifiers.md) to make your own lists as powerful as these.