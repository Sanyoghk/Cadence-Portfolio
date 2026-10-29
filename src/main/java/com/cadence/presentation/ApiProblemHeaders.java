package com.cadence.presentation;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiProblemHeaders implements ResponseBodyAdvice<Object> {
  @Override
  public boolean supports(
      MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter parameter,
      MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (body instanceof ProblemDetail) {
      response.getHeaders().setCacheControl("no-store");
    }
    return body;
  }
}
