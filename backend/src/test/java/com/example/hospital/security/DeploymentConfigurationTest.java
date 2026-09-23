package com.example.hospital.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DeploymentConfigurationTest {
  private MockEnvironment valid() {
    return new MockEnvironment()
        .withProperty("spring.datasource.url", "jdbc:postgresql://db:5432/hospital")
        .withProperty("spring.datasource.password", "s3cret-database-password")
        .withProperty("server.servlet.session.cookie.secure", "true")
        .withProperty("app.ai.mode", "local")
        .withProperty("app.environment", "development")
        .withProperty("app.registration.expiry-minutes", "30");
  }

  @Test
  void acceptsAMinimalValidConfiguration() {
    assertThat(DeploymentConfiguration.problems(valid())).isEmpty();
  }

  @Test
  void reportsEveryProblemByVariableNameWithoutValues() {
    var env =
        valid()
            .withProperty("spring.datasource.password", "")
            .withProperty("spring.datasource.url", "jdbc:h2:mem:x")
            .withProperty("app.bootstrap-password", "short")
            .withProperty("app.public-url", "https://ward.example.org/app")
            .withProperty("app.email.resend-key", "re_secret_value")
            .withProperty("app.registration.expiry-minutes", "0")
            .withProperty("app.ai.mode", "external")
            .withProperty("app.ai.url", "ftp://model");

    var problems = DeploymentConfiguration.problems(env);

    assertThat(problems)
        .anyMatch(p -> p.startsWith("DATABASE_PASSWORD"))
        .anyMatch(p -> p.startsWith("DATABASE_URL"))
        .anyMatch(p -> p.startsWith("BOOTSTRAP_PASSWORD"))
        .anyMatch(p -> p.startsWith("PUBLIC_APP_URL"))
        .anyMatch(p -> p.startsWith("EMAIL_FROM"))
        .anyMatch(p -> p.startsWith("EMAIL_CONFIRMATION_MINUTES"))
        .anyMatch(p -> p.startsWith("AI_URL"))
        .anyMatch(p -> p.startsWith("AI_MODEL"));
    assertThat(String.join(" ", problems))
        .doesNotContain("re_secret_value")
        .doesNotContain("short")
        .doesNotContain("ward.example.org");
  }

  @Test
  void senderWithoutKeyOnlyDisablesEmail() {
    assertThat(DeploymentConfiguration.problems(valid().withProperty("app.email.from", "Ward <a@b.org>")))
        .isEmpty();
  }

  @Test
  void requiresSecureCookiesBehindAnHttpsOrigin() {
    var env =
        valid()
            .withProperty("app.public-url", "https://ward.example.org")
            .withProperty("server.servlet.session.cookie.secure", "false");
    assertThat(DeploymentConfiguration.problems(env)).containsExactly(
        "COOKIE_SECURE must be true when PUBLIC_APP_URL uses https.");
  }

  @Test
  void failsStartupWithAllProblemsListed() {
    var env = valid().withProperty("app.ai.mode", "cloud");
    assertThatThrownBy(() -> new DeploymentConfiguration(env))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_MODE");
  }

  @Test
  void destructiveDemoResetRequiresADemoEnvironment() {
    assertThat(DeploymentConfiguration.problems(valid().withProperty("app.demo", "true")))
        .containsExactly(
            "DEMO_MODE=true requires APP_ENVIRONMENT=demo (a dedicated synthetic database).");
    assertThat(
            DeploymentConfiguration.problems(
                valid().withProperty("app.demo", "true").withProperty("app.environment", "demo")))
        .isEmpty();
  }

  @Test
  void productionRejectsSyntheticSeeding() {
    var env = valid().withProperty("app.environment", "production").withProperty("app.seed", "true");
    assertThat(DeploymentConfiguration.problems(env))
        .containsExactly("DEMO_SEED must be false when APP_ENVIRONMENT=production.");
    assertThat(DeploymentConfiguration.problems(valid().withProperty("app.environment", "prod")))
        .anyMatch(p -> p.startsWith("APP_ENVIRONMENT"));
  }
}
