package ravenworks.fizz.web.controller;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ravenworks.fizz.common.model.ApiResponse;
import ravenworks.fizz.service.service.JobService;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(JobService.ResourceNotFoundException.class)
    public ApiResponse<Void> handleNotFound(JobService.ResourceNotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    @ExceptionHandler(JobService.ConflictException.class)
    public ApiResponse<Void> handleConflict(JobService.ConflictException e) {
        return ApiResponse.error(409, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleBadRequest(IllegalArgumentException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleGeneral(Exception e) {
        return ApiResponse.error(500, "Internal error: " + e.getMessage());
    }

}
