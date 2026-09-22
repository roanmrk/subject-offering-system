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
        String method = request.getMethod();

        // Allow OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        // ============================================
        // PUBLIC endpoints — skip JWT validation
        // ============================================
        if (isPublicPath(path)) {
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

    /**
     * Returns true if the path is public (no JWT needed).
     */
    private boolean isPublicPath(String path) {
        // ============================================
        // API public endpoints
        // ============================================
        if (path.equals("/api/admin/users/login")) return true;
        if (path.equals("/api/schedule/test")) return true;
        if (path.startsWith("/error")) return true;

        // ============================================
        // Frontend static assets (React build)
        // ============================================
        if (path.equals("/")) return true;
        if (path.equals("/index.html")) return true;
        if (path.equals("/favicon.ico")) return true;
        if (path.equals("/manifest.json")) return true;
        if (path.equals("/robots.txt")) return true;
        if (path.equals("/logo192.png")) return true;
        if (path.equals("/logo512.png")) return true;
        if (path.equals("/asset-manifest.json")) return true;
        if (path.startsWith("/static/")) return true;
        if (path.startsWith("/assets/")) return true;

        // ============================================
        // Frontend client-side routes
        // (these get forwarded to index.html)
        // ============================================
        if (path.equals("/login")) return true;
        if (path.equals("/forgot-password")) return true;
        if (path.equals("/register")) return true;

        // ============================================
        // File extensions — always public (js, css, png, etc.)
        // ============================================
        if (path.matches(".*\\.(js|css|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot|map|json)$")) {
            return true;
        }

        return false;
    }
}