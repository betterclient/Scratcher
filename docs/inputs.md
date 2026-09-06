# Inputs

Scratcher supports function returns:

```
import looks::say;
import sensing::ask;

on GreenFlag {
    str name = ask("What's your name?");
    say("Your name is ${name}!");
}
```

## New concepts

- New standard library: `import sensing::ask;`: Import the `ask` block from the `sensing` library
- Locals and function returns: `str name = ask(..);`: A local variable with the `str` type, this stores strings, we ask the user for their name and store the result in this variable.
- String interpolation: We include the local variable `name` in the final `say` by using the string interpolation syntax `${expr}`


### Notes

The `ask` function compiles directly to the scratch block:
![ask block](images/ask.png)

## Exercises

- Try to make an app that asks 2 questions and then shows both answers in the final `say` block.
- Next up, let's make a [recursive Fibonacci sequence calculator](fibonacci.md)!
