import utils::*;
import looks::say;

sealed enum Result<T, E> {
    Success(T out),
    Failure(E error)
}

struct Holder(int x);

auto a = Holder(1515);

on GreenFlag {
    when(tryGet()) {
        Result.Success suc -> {
            say("Success! ${suc.out.x}");
        }
        Result.Failure fail -> {
            say("Error message: ${fail.error}");
        }
    }
}

warp Result<Holder, str> tryGet() {
    return Result.Failure("error!!!") if random(1, 15) == 8;

    return Result.Success(a);
}