package com.example.hospital.security;

import com.example.hospital.api.Errors;
import com.example.hospital.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class DepartmentScopeFilter extends OncePerRequestFilter {
  private final AppUserRepository users;
  private final WorkspaceAccess workspaces;
  private final ObjectMapper json;
  private final SessionLifetime lifetime;

  public DepartmentScopeFilter(
      AppUserRepository users, WorkspaceAccess workspaces, ObjectMapper json, SessionLifetime lifetime) {
    this.users = users;
    this.workspaces = workspaces;
    this.json = json;
    this.lifetime = lifetime;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest r, HttpServletResponse s, FilterChain c)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
      var session = r.getSession(false);
      if (session != null && lifetime.expired(session)) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        s.setStatus(401);
        s.setContentType("application/json");
        json.writeValue(
            s.getWriter(),
            Errors.body(
                401,
                "SESSION_EXPIRED",
                "Your session reached its maximum length. Please sign in again.",
                r.getRequestURI()));
        return;
      }
      var u = users.findByUsername(auth.getName());
      var stamp = session == null ? null : session.getAttribute("credentialStamp");
      if (u.isEmpty()
          || !u.get().isEnabled()
          || (stamp != null && !stamp.equals(u.get().getSessionStamp()))
          || (session != null
              && session.getAttribute("accountId") != null
              && !session.getAttribute("accountId").equals(u.get().getId()))) {
        SecurityContextHolder.clearContext();
        if (r.getSession(false) != null) r.getSession(false).invalidate();
      } else {
        try {
          String requested = r.getHeader("X-Department-Id");
          if (requested == null) requested = r.getParameter("departmentId");
          if (requested == null && session != null) {
            var stored = session.getAttribute("departmentId");
            requested = stored == null ? null : stored.toString();
          }
          var scope = workspaces.resolve(u.get(), requested);
          DepartmentContext.set(scope);
          if (session != null && scope.id() > 0) {
            session.setAttribute("departmentId", scope.id());
          }
        } catch (com.example.hospital.api.ApiException e) {
          s.setStatus(e.getStatus());
          s.setContentType("application/json");
          json.writeValue(s.getWriter(), Errors.body(e.getStatus(), e.code, e.getMessage(), r.getRequestURI()));
          return;
        }
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    u.get().getUsername(),
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
}
