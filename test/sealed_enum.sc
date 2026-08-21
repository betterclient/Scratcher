import extensions;
import looks;
import utils;

on GreenFlag {
    auto out = runSomething();

    when(out) {
        Success -> {
            looks::say("success! ${out.message}");
        }
        Failure -> {
            looks::say("failure :( ${out.errorCode}");
        }
    }
}

Result runSomething() {
    return if(utils::random(0, 5) == 3) {
        Result.Failure(67)
    } else {
        Result.Success("wower!")
    };
}

sealed enum Result {
    Success(str message),
    Failure(int errorCode)
}