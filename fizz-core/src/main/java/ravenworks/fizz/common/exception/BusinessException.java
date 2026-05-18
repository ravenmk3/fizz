package ravenworks.fizz.common.exception;

import lombok.Getter;
import lombok.NonNull;


@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, @NonNull String message) {
        super(message);
        this.code = code;
    }

}
