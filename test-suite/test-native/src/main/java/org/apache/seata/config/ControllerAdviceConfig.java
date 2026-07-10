package org.apache.seata.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

@ControllerAdvice({"org.apache.seata"})
public class ControllerAdviceConfig {

    /**
     * Set HTTP response status code to 500 for distributed transaction rollback
     */
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Map<String, Object> exception(
            HttpServletRequest request, HttpServletResponse response, Exception exception) {
        String message = exception.getMessage();
        return Map.of("status", 500, "error", message);
    }
}
