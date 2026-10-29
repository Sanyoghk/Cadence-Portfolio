package com.cadence.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Embeddable
public class PublishingCadence {

  @Column(name = "posts_per_week")
  private int postsPerWeek;

  @Column(name = "publishing_timezone")
  private String timezone;

  @ElementCollection
  @CollectionTable(
      name = "strategy_best_publishing_times",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_TIME)
  @Column(name = "publishing_time")
  @OrderColumn(name = "publishing_time_order")
  private List<LocalTime> bestTimes = new ArrayList<>();

  protected PublishingCadence() {}

  public PublishingCadence(int postsPerWeek, String timezone, List<LocalTime> bestTimes) {
    setPostsPerWeek(postsPerWeek);
    setTimezone(timezone);
    setBestTimes(bestTimes);
  }

  public int getPostsPerWeek() {
    return postsPerWeek;
  }

  public void setPostsPerWeek(int postsPerWeek) {
    if (postsPerWeek < 1) {
      throw new IllegalArgumentException("Posts per week must be greater than zero");
    }

    this.postsPerWeek = postsPerWeek;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    if (timezone == null || timezone.isBlank()) {
      throw new IllegalArgumentException("Timezone must not be empty");
    }

    /*
     * Проверяет, что переданная строка является настоящим
     * часовым поясом, например Europe/London или Asia/Almaty.
     */
    ZoneId.of(timezone);

    this.timezone = timezone;
  }

  public List<LocalTime> getBestTimes() {
    return new ArrayList<>(bestTimes);
  }

  public void setBestTimes(List<LocalTime> bestTimes) {
    if (bestTimes == null) {
      this.bestTimes = new ArrayList<>();
      return;
    }

    if (bestTimes.stream().anyMatch(time -> time == null)) {
      throw new IllegalArgumentException("Best publishing times must not contain null values");
    }

    this.bestTimes = new ArrayList<>(bestTimes);
  }
}
