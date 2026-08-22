import extensions;
import looks;
import utils;

on GreenFlag {
    auto out = runSomething();

    when(out) {
        Result.Success -> {
            looks::say("success! ${(out as Result.Success).out}");
        }
        Result.Failure -> {
            looks::say("failure :(");
        }
    }
}

Result<str> runSomething() {
    return if(utils::random(0, 5) == 3) {
        Result.Failure(67)
    } else {
        Result.Success("wower!")
    };
}

sealed enum Result<T> {
    Success(T out),
    Failure(int errorCode)
}
