package com.cadence.infrastructure.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provider deadlines also require finite PostgreSQL statement and lock waits. */
@Configuration(proxyBeanMethods = false)
public class PostgreSqlEvidenceConnectionPolicy {
  static final String TIMEOUT_SQL = "SET statement_timeout = '10s'; SET lock_timeout = '5s'";

  @Bean
  static BeanPostProcessor evidenceDatabaseTimeouts() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessBeforeInitialization(Object bean, String name) {
        if (bean instanceof HikariDataSource datasource
            && datasource.getJdbcUrl() != null
            && datasource.getJdbcUrl().startsWith("jdbc:postgresql:")) {
          String configured = datasource.getConnectionInitSql();
          if (configured != null && !configured.equals(TIMEOUT_SQL))
            throw new IllegalArgumentException("Invalid evidence database timeout policy");
          datasource.setConnectionInitSql(TIMEOUT_SQL);
        }
        return bean;
      }
    };
  }
}
