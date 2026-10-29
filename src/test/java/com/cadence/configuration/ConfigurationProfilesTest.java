package com.cadence.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ConfigurationProfilesTest {

  @Test
  void fileScanPollingIsOffByDefaultAndCannotActivateInProduction() throws IOException {
    assertEquals(
        "${CADENCE_FILE_SCAN_WORKER_ENABLED:false}",
        load("application.properties").getProperty("cadence.evidence.file-scan-worker-enabled"));
    for (String profiles : java.util.List.of("prod", "local,prod")) {
      new org.springframework.boot.test.context.runner.ApplicationContextRunner()
          .withPropertyValues(
              "spring.profiles.active=" + profiles,
              "cadence.evidence.file-scan-worker-enabled=true")
          .withUserConfiguration(com.cadence.infrastructure.ingest.FileScanScheduling.class)
          .run(
              context -> {
                assertEquals(null, context.getStartupFailure());
                assertTrue(
                    context
                        .getBeansOfType(com.cadence.infrastructure.ingest.FileScanScheduling.class)
                        .isEmpty());
              });
    }
  }

  @Test
  void commonConfigurationDisablesUnsafeWebDefaults() throws IOException {
    Properties properties = load("application.properties");

    assertEquals("local", properties.getProperty("spring.profiles.default"));
    assertEquals("false", properties.getProperty("spring.jpa.open-in-view"));
    assertEquals("UTC", properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"));
    assertEquals("UTC", properties.getProperty("spring.jackson2.time-zone"));
    assertEquals("never", properties.getProperty("server.error.include-stacktrace"));
  }

  @Test
  void localProfileUsesExplicitH2Configuration() throws IOException {
    Properties properties = load("application-local.properties");

    assertTrue(properties.getProperty("spring.datasource.url").startsWith("jdbc:h2:mem:"));
    assertEquals("create-drop", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertEquals("false", properties.getProperty("spring.flyway.enabled"));
  }

  @Test
  void testProfileUsesExplicitIsolatedH2Configuration() throws IOException {
    Properties properties = load("application-test.properties");

    assertEquals("true", properties.getProperty("spring.datasource.generate-unique-name"));
    assertFalse(properties.containsKey("spring.datasource.url"));
    assertEquals("org.h2.Driver", properties.getProperty("spring.datasource.driver-class-name"));
    assertEquals("create-drop", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertEquals("false", properties.getProperty("spring.flyway.enabled"));
  }

  @Test
  void closingAnotherTestContextCannotDestroyTheFirstContextsDatabase() throws Exception {
    var properties = load("application-test.properties");
    var runner =
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withConfiguration(
                org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class))
            .withPropertyValues(
                properties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toArray(String[]::new));
    runner.run(
        first -> {
          assertTrue(first.getStartupFailure() == null);
          var a =
              new org.springframework.jdbc.core.JdbcTemplate(
                  first.getBean(javax.sql.DataSource.class));
          a.execute("create table context_isolation(id integer primary key)");
          a.update("insert into context_isolation(id) values(1)");
          runner.run(
              second -> {
                assertTrue(second.getStartupFailure() == null);
                var b =
                    new org.springframework.jdbc.core.JdbcTemplate(
                        second.getBean(javax.sql.DataSource.class));
                // This would fail on the old shared cadence-test URL and closing one JPA context
                // would drop the other context's schema. Exercise independent DDL, not a property
                // string.
                b.execute("create table context_isolation(id integer primary key)");
                b.execute("drop table context_isolation");
              });
          assertEquals(
              1, a.queryForObject("select count(*) from context_isolation", Integer.class));
        });
  }

  @Test
  void productionProfileRequiresPostgresEnvironmentAndOnlyValidatesSchema() throws IOException {
    Properties properties = load("application-prod.properties");

    assertEquals("${CADENCE_DB_URL}", properties.getProperty("spring.datasource.url"));
    assertEquals(
        "org.postgresql.Driver", properties.getProperty("spring.datasource.driver-class-name"));
    assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
    assertEquals("true", properties.getProperty("spring.flyway.enabled"));
    assertFalse(properties.getProperty("spring.datasource.url").contains("h2"));
  }

  private Properties load(String resourceName) throws IOException {
    Properties properties = new Properties();
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
      if (input == null) {
        throw new IllegalStateException("Missing test resource: " + resourceName);
      }
      properties.load(input);
    }
    return properties;
  }
}
