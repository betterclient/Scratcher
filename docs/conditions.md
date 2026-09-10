# Conditions
Scratcher supports conditions and branching

## if
The if condition is very simple:
```
if(condition) {
    ...
}
```
The code inside the statement will only be executed if the condition evaluates to `true`.

**Very important**: Unlike other languages, scratcher's `&&` operator is not short-circuiting. This means both sides of the operator will execute no matter what.

## if else
The if else statement is also a very simple condition:
```
if(condition) {
    ...
} else {
    ...
}
```
If the `condition` evaluates to `true`, the first block will be executed. Otherwise, the second block will be executed.

The `if else` statement can also be used inside expressions.
```
function(5, if(condition) {
    ...
} else {
    ...
});
```
The condition will evaluate and the code inside will be executed just like a normal if else block.
But you will need to provide a return value because it's inline.
The return value is provided by having a value as the last line of the if statement.

```
if(condition) {
    ...
    value
} else {
    ...
    value
}
```

## if else if
You can use the `else if` statement to provide more branches to your if statements.
```
if(condition) {
    ...
} else if(condition2) {
    ...
} else {
    ...
}
```

You can also use the `else if` inside expressions.
```
function(5, if(condition) {
    ...
} else if(condition2) {
    ...
} else {
    ...
});
```

### when
The `when` statement is used to express more complex states easily.
```
when(subject) {
    value1 -> { ... }
    value2 -> ...
    value3 -> {}
}
```
For example, this may be used like:
```
int myVariable = ...;
when(myVariable) {
    5 -> { 
        say("myVariable is 5!"); 
    }
    6 -> {
        say("myVariable is 6!");
    }
    else -> {
        say("myVariable is not 5 or 6!");
    }
}
```
The when statement can be used inline too.
```
function(5, when(subject) {
    value1 -> { .... value }
    value2 -> value
    value3 -> { ... value }
    else -> value
});
```

## Next up
- Learn about the [primitives](primitives.md)!