package com.cadence.presentation;

import static org.assertj.core.api.Assertions.*;

import com.cadence.http.ApiPagination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApiPaginationTest {
  @ParameterizedTest
  @CsvSource({"-1,20", "10001,20", "0,0", "0,101", "2147483647,100"})
  void rejectsUnboundedOffsetsAndSizes(int page, int size) {
    assertThatThrownBy(() -> ApiPagination.request(page, size, "name", "asc", "name"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowsOnlyDeclaredFieldsAndDirections() {
    assertThatThrownBy(
            () -> ApiPagination.request(0, 20, "workspace.users.password", "asc", "name"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ApiPagination.request(0, 20, "name", "arbitrary", "name"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equalSortValuesHaveUniqueDeterministicTieBreaker() {
    var request = ApiPagination.request(10000, 100, "name", "desc", "name");
    assertThat(request.getOffset()).isEqualTo(1000000);
    assertThat(request.getSort().getOrderFor("name").isDescending()).isTrue();
    assertThat(request.getSort().getOrderFor("id").isDescending()).isTrue();
  }
}
