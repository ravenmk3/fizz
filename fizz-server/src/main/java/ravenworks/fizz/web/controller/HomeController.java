package ravenworks.fizz.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ravenworks.fizz.common.model.ApiResponse;


/**
 * @author Raven
 */
@Slf4j
@Validated
@RestController
public class HomeController {

    @GetMapping("/")
    public ApiResponse<Void> home() {
        return ApiResponse.success();
    }

}
