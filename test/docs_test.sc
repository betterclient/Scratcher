import looks::say;
import sensing::ask;
import cast;

on GreenFlag {
    int number = cast::toInt(ask("Fibonnaci number?"));
}