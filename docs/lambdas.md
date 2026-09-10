# Lambdas
Lambdas are an incredibly powerful expressive kind of [high order functions](https://en.wikipedia.org/wiki/Higher-order_function).

Scratcher provides very flexible lambda support.
To define a function that can take in a lambda, use the **function argument** type.

```
(arguments) -> return type
```
for example:
```
void invoke(() -> void myLambdaArgument) {
    
}
```
This can also be used in local variables, generics and everywhere else that wants a type.

To call this function, call it directly:
```
myLambdaArgument();
```
It behaves like a normal function call, but it is way more powerful.

When actually calling functions that take in a `function argument`, you have 2 choices:
- Lambda
- Function pointer

Let's start with function pointers as they are simpler, say we have this:
```
void hi() {
    say("hi!");
}

void invoke(() -> void myLambdaArgument) {
    myLambdaArgument();
}
```
We can call the `invoke` function by passing in `hi` using the **function pointer** syntax:
```
invoke(&hi);
```
This takes a reference to `hi` and calls `invoke` on it. Then `invoke` calls `hi`. `invoke` can do anything with `hi`, like storing it in a variable and then using it later on.

Now let's learn about lambdas:

- Lambdas are way simpler because you don't have to create a function just to pass into the argument
- The compiler does it all automatically
```
invoke(() -> {
    say("hello!");
});
```
- You can also use local variables from outside the lambda
- For example:
```
int amountCalled = 0;
invoke(() -> {
    say("hi!");
    amountCalled++;
});
say("called: ${amountCalled}");
```
- In this example, at the end of the function, we will have "called: 1" printed on the screen.

## Now let's improve our linked list
One of the best ways to use lambdas is with list manipulation.

We will make a `forEach` function to show:
```
warp <T> void LinkedList<T>.forEach((T) -> void action) {
    Node<T>? current = this.first;

    while(current != null) {
        action(current!!.value);
        current = current!!.next;
    }
}
```
This new function automatically calls the `action` on every object in the linked list, removing the need for a manual iteration.

Let's rewrite our biggest number finder using this function:
```
on GreenFlag {
    LinkedList<int> myList = newLinkedList(5);

    repeat(15) {
        myList.add(random(1, 150));
    }

    int currentBiggest = 0;
    myList.forEach((int obj) -> {
        if(obj > currentBiggest) {
            currentBiggest = obj;
        }
    });

    say("The biggest value in our linked list is ${currentBiggest}");
}
```
As you can see, this new version is so much more compact and actually focuses on what the code does instead of traversing the list.

## Exercises
- Make a `map` function that creates a new linked list by mapping all the values of the current linked list with an `action`

Use this template:
```
warp <T, R> LinkedList<R> LinkedList<T>.map((T) -> R action) { ... }
```

- Learn about the [native collections](native_collections.md) in scratcher!