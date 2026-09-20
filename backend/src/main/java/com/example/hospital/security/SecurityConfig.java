package com.example.hospital.security;

import com.example.hospital.api.Errors;
import com.example.hospital.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService userDetails(AppUserRepository users) {
    return username ->
        users
            .findByUsername(username)
            .map(
                u ->
                    User.withUsername(u.username)
                        .password(u.passwordHash)
                        .roles(u.role)
                        .disabled(!u.enabled)
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http, ObjectMapper json, AppUserRepository users, WorkspaceAccess workspaces)
      throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/health",
                    "/api/v1/demo/status", "/api/v1/demo/login", "/api/v1/registration/status",
                    "/api/v1/registration/signup", "/api/v1/registration/verify", "/api/v1/registration/resend")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/v1/management/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/portal/**")
                    .hasRole("PATIENT")
                    .anyRequest()
                    .hasAnyRole("ADMIN", "MEDICAL_STAFF", "DOCTOR"))
        .requestCache(c -> c.disable())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/v1/auth/login")
                    .successHandler(
                        (r, s, a) -> {
                          var u = users.findByUsername(a.getName()).orElseThrow();
                          u.lastLoginAt = Instant.now();
                          users.save(u);
                          r.getSession().setAttribute("credentialStamp", u.passwordHash);
                          r.getSession().setAttribute("accountId", u.id);
                          s.setContentType("application/json");
                          json.writeValue(s.getWriter(), u);
                        })
                    .failureHandler(
                        (r, s, e) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          json.writeValue(
                              s.getWriter(),
                              Errors.body(
                                  401,
                                  "INVALID_CREDENTIALS",
                                  "Invalid username or password.",
                                  r.getRequestURI()));
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/v1/auth/logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID")
                    .logoutSuccessHandler((r, s, a) -> s.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (r, s, x) -> {
                          s.setStatus(401);
                          s.setContentType("application/json");
                          json.writeValue(
                              s.getWriter(),
                              Errors.body(
                                  401, "UNAUTHENTICATED", "Please sign in.", r.getRequestURI()));
                        })
                    .accessDeniedHandler(
                        (r, s, x) -> {
                          s.setStatus(403);
                          s.setContentType("application/json");
                          json.writeValue(
                              s.getWriter(),
                              Errors.body(
                                  403,
                                  "ACCESS_DENIED",
                                  "Operation denied. Refresh your session if it has expired.",
                                  r.getRequestURI()));
                        }))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest r, HttpServletResponse s, FilterChain c)
                  throws ServletException, IOException {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null
                    && auth.isAuthenticated()
                    && !auth.getName().equals("anonymousUser")) {
                  var u = users.findByUsername(auth.getName());
                  var session = r.getSession(false);
                  var stamp = session == null ? null : session.getAttribute("credentialStamp");
                  if (u.isEmpty()
                      || !u.get().enabled
                      || (stamp != null && !stamp.equals(u.get().passwordHash))
                      || (session != null && session.getAttribute("accountId") != null
                          && !session.getAttribute("accountId").equals(u.get().id))) {
                    SecurityContextHolder.clearContext();
                    if (r.getSession(false) != null) r.getSession(false).invalidate();
                  } else {
                    try {
                      String requested = r.getHeader("X-Department-Id");
                      if (requested == null && session != null) {
                        var stored = session.getAttribute("departmentId");
                        requested = stored == null ? null : stored.toString();
                      }
                      var scope = workspaces.resolve(u.get(), requested);
                      DepartmentContext.set(scope);
                    } catch (com.example.hospital.api.ApiException e) {
                      s.setStatus(e.status);
                      s.setContentType("application/json");
                      json.writeValue(s.getWriter(), Errors.body(e.status, e.code, e.getMessage(), r.getRequestURI()));
                      return;
                    }
                    SecurityContextHolder.getContext()
                        .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                u.get().username,
                                null,
                                java.util.List.of(
                                    new SimpleGrantedAuthority("ROLE_" + DepartmentContext.current().role()))));
                  }
                }
                s.setHeader("Cache-Control", "no-store");
                try {
                  c.doFilter(r, s);
                } finally {
                  DepartmentContext.clear();
                }
              }
            },
            AuthorizationFilter.class);
    return http.build();
  }
}
