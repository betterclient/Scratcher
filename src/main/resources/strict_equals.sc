import case_sensitive_equals as self;
import extensions_internal as helper;
import cast;

warp bool equalsCC(char left, char right) {
    return false if left != right;
    return self::isUppercase(left) == self::isUppercase(right);
}

warp bool equalsSS(str left, str right) {
    return false if helper::length(left) != helper::length(right);
    return true if left == "";

    int index = 1;
    repeat(helper::length(left)) {
        return false if(
            !equalsCC(helper::charAt(left, index), helper::charAt(right, index))
        );
        index++;
    }

    return true;
}