package org.example.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.context.ThreadLocalContextProvider;
import org.example.model.QueryContext;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

public class RequestContextInterceptor implements HandlerInterceptor {

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        ThreadLocalContextProvider.clear();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String id = UUID.randomUUID().toString();
        String name = request.getMethod() + " " + request.getRequestURI();

        ThreadLocalContextProvider.set(new QueryContext(id, name));

        return true;
    }
}
