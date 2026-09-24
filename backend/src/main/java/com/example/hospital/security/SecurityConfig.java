package com.example.hospital.security;

import com.example.hospital.api.Errors;
import com.example.hospital.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
@EnableScheduling
@EnableConfigurationProperties(LoginBackoffProperties.class)
public class SecurityConfig {
  @Bean
  Clock loginBackoffClock() {
    return Clock.systemUTC();
  }

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
                    User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles(u.getRole())
                        .disabled(!u.isEnabled())
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http,
      ObjectMapper json,
      AppUserRepository users,
      WorkspaceAccess workspaces,
      LoginBackoff backoff,
      SessionLifetime lifetime,
      ClientAddressResolver clientAddresses,
      com.example.hospital.service.SecurityEventService securityEvents)
      throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/health", "/api/v1/health/live", "/api/v1/health/ready",
                    "/api/v1/demo/status", "/api/v1/demo/login", "/api/v1/registration/status",
                    "/api/v1/registration/hospitals",
                    "/api/v1/registration/signup", "/api/v1/registration/verify", "/api/v1/registration/resend",
                    "/api/v1/registration/recover")
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
                          backoff.success(a.getName(), clientAddresses.sourceAddress(r));
                          u.setLastLoginAt(Instant.now());
                          if (u.getSessionStamp() == null || u.getSessionStamp().isBlank())
                            u.setSessionStamp(SessionStamps.next());
                          users.save(u);
                          r.getSession().setAttribute("credentialStamp", u.getSessionStamp());
                          SessionLifetime.markAuthenticated(r.getSession());
                          r.getSession().setAttribute("accountId", u.getId());
                          s.setContentType("application/json");
                          json.writeValue(s.getWriter(), com.example.hospital.api.Views.account(u));
                        })
                    .failureHandler(
                        (r, s, e) -> {
                          backoff.failure(
                              r.getParameter("username"), clientAddresses.sourceAddress(r));
                          reportFailedLogin(securityEvents, r.getParameter("username"));
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
                        c ->
                            c.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                    .contentTypeOptions(Customizer.withDefaults())
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(
                        p -> p.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .httpStrictTransportSecurity(
                        t -> t.includeSubDomains(true).preload(true).maxAgeInSeconds(63072000))
                    .permissionsPolicyHeader(
                        p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()")))
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest r, HttpServletResponse s, FilterChain c)
                  throws ServletException, IOException {
                if ("POST".equalsIgnoreCase(r.getMethod())
                    && (r.getContextPath() + "/api/v1/auth/login").equals(r.getRequestURI())
                    && backoff.blocked(
                        r.getParameter("username"), clientAddresses.sourceAddress(r))) {
                  reportFailedLogin(securityEvents, r.getParameter("username"));
                  s.setStatus(401);
                  s.setContentType("application/json");
                  json.writeValue(
                      s.getWriter(),
                      Errors.body(
                          401,
                          "INVALID_CREDENTIALS",
                          "Invalid username or password.",
                          r.getRequestURI()));
                  return;
                }
                c.doFilter(r, s);
              }
            },
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new DepartmentScopeFilter(users, workspaces, json, lifetime),
            AuthorizationFilter.class);
    return http.build();
  }

  /** The security review queue must never change the sign-in response. */
  private static void reportFailedLogin(
      com.example.hospital.service.SecurityEventService securityEvents, String username) {
    try {
      securityEvents.failedLogin(username);
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
          .warn("Failed sign-in was not added to the security review queue: {}", e.getClass().getSimpleName());
    }
  }
}
