package com.cadence.presentation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ReadinessControllerTest {
  @Test
  void infrastructureFailureDoesNotLeakConfiguration() throws Exception {
    var db = mock(DataSource.class);
    when(db.getConnection()).thenThrow(new SQLException("jdbc:private-password"));
    var r = new ReadinessController(db).ready();
    assertThat(r.getStatusCode().value()).isEqualTo(503);
    assertThat(r.getBody()).isInstanceOf(org.springframework.http.ProblemDetail.class);
    assertThat(((org.springframework.http.ProblemDetail) r.getBody()).getDetail())
        .doesNotContain("jdbc", "password");
  }

  @Test
  void queryIsBoundedAndResourcesCloseEvenWhenDatabaseFails() throws Exception {
    var db = mock(DataSource.class);
    var c = mock(Connection.class);
    var s = mock(Statement.class);
    when(db.getConnection()).thenReturn(c);
    when(c.createStatement()).thenReturn(s);
    when(s.executeQuery("SELECT 1")).thenThrow(new SQLTimeoutException("private"));
    assertThat(new ReadinessController(db).ready().getStatusCode().value()).isEqualTo(503);
    verify(s).setQueryTimeout(1);
    verify(s).close();
    verify(c).close();
  }
}
