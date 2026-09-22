package com.earist.ccs.scheduler.filter;

import com.earist.ccs.scheduler.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Public endpoints — no token required
        if (path.equals("/api/admin/users/login")
                || path.equals("/api/schedule/test")
                || path.startsWith("/error")) {
            chain.doFilter(request, response);
            return;
        }

        // Allow OPTIONS (preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            
            String email = claims.getSubject();
            String role = (String) claims.get("role");
            
            // ✅ CRITICAL: Tell Spring Security who the user is
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
            
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    email, 
                    null, 
                    Collections.singletonList(authority)
                );
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            request.setAttribute("email", email);
            request.setAttribute("role", role);
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
        }
    }
}