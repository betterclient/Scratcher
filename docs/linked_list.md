# Make a linked list
You now know how to use structs, generics and the nullability system. This is enough to make a simple linked list.

A [linked list](https://en.wikipedia.org/wiki/Linked_list) is a collection of values that all point to the next one.

Let's start by making our `LinkedList` struct.
```
struct LinkedList<T>(
    Node<T> first
);
```
This struct holds the pointer to the first `Node` in our linked list, `Node` is undefined right now, let's make a `Node`:
```
struct Node<T>(
    T value,
    Node<T>? next
);
```
The `next` node is made nullable because the list may end at that node.

Let's make a simple function that creates a single element `LinkedList`
```
warp <T> LinkedList<T> newLinkedList(T object) {
    return LinkedList(
        Node(object, null)
    );
}
```
This creates a new `LinkedList` that has a `Node` that points to a non-existent next node, making it size 1

Now let's make a helper function so that we can find the last element in our linked list:
```
private warp <T> Node<T> last(LinkedList<T> list) {
    Node<T> current = list.first;
    
    while(current.next != null) {
        current = current.next!!;
    }
    
    return current;
}
```
- With this, we get our first look into the `while` statement, the `while` statement repeats a block of code while the condition given is `true`.
- We mark this function `private` because only we need to use this function.

We can make this function even better to use by making it a `receiver function`:
- Receiver functions are special functions that take in a value, using the `.` syntax.
- Simply change the function signature and change the `list` uses to `this` 
```
private warp <T> Node<T> LinkedList<T>.last() {
    Node<T> current = this.first;
    ...
}
```
- This allows the function to be called with the list, instead of calling with just a plain argument:
```
auto last = myList.last();
```

Let's make a new `add` function that adds a new `Node` at the very end of the list:
```
warp <T> void LinkedList<T>.add(T object) {
    auto last = this.last();

    last.next = Node(object, null);
}
```
This function is very simple, just gets the last element of our list, and makes the next one our `object`.

You can make this simpler by using just `last()`, because the `this` receiver is inferred automatically due to `add` also being a receiver.
```
auto last = last();
```

Let's make a function that returns the length of our list:
```
warp <T> int LinkedList<T>.length() {
    Node<T>? current = this.first;
    int length = 0;
    
    while(current != null) {
        current = current!!.next;
        length++;
    }
    
    return length;
}
```

## Using our linked list in an actual program
Let's make a list of integers and populate them randomly:
```
import utils::random;
import looks::say;

on GreenFlag {
    LinkedList<int> myList = newLinkedList(5);
    
    repeat(15) {
        myList.add(random(1, 150));
    }
}
```
2 new things!
- The `random` function: The standard library provides a `random` function that returns a random value between the given 2 integers
- The `repeat` statement: This repeats a given block the given number of times! In this case, the `myList.add` block will run 15 times

Now let's traverse our list and find the biggest value!

```
Node<int>? current = myList.first;
int currentBiggest = 0;

while(current != null) {
    if(current!!.value > currentBiggest) {
        currentBiggest = current!!.value;
    }
    current = current!!.next;
}

say("The biggest value in our linked list is ${currentBiggest}");
```

Nice! We've implemented a Linked List and traversed it, all in scratcher!

## Exercises
- Make a [doubly linked list](https://en.wikipedia.org/wiki/Doubly_linked_list).
- Expand upon the linked list by learning about [lambdas and function arguments](lambdas.md)!