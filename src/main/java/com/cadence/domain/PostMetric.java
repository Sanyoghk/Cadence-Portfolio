package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_metrics")
public class PostMetric extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "post_id", nullable = false)
  private SocialPost post;

  @Column(nullable = false)
  private int reach;

  @Column(nullable = false)
  private int engagement;

  @Column(nullable = false)
  private int saves;

  @Column(nullable = false)
  private int signups;

  @Column(name = "profile_to_site_clicks", nullable = false)
  private int profileToSiteClicks;

  protected PostMetric() {}

  public PostMetric(
      SocialPost post, int reach, int engagement, int saves, int signups, int profileToSiteClicks) {
    setPost(post);
    setReach(reach);
    setEngagement(engagement);
    setSaves(saves);
    setSignups(signups);
    setProfileToSiteClicks(profileToSiteClicks);
  }

  /**
   * Возвращает коэффициент вовлечённости в процентах.
   *
   * <p>Например: reach = 1000 engagement = 120 результат = 12.0
   */
  public double engagementRate() {
    if (reach == 0) {
      return 0.0;
    }

    return ((double) engagement / reach) * 100.0;
  }

  public SocialPost getPost() {
    return post;
  }

  public void setPost(SocialPost post) {
    if (post == null) {
      throw new IllegalArgumentException("Post must not be null");
    }

    this.post = post;
  }

  public int getReach() {
    return reach;
  }

  public void setReach(int reach) {
    validateNonNegative(reach, "Reach");
    this.reach = reach;
  }

  public int getEngagement() {
    return engagement;
  }

  public void setEngagement(int engagement) {
    validateNonNegative(engagement, "Engagement");
    this.engagement = engagement;
  }

  public int getSaves() {
    return saves;
  }

  public void setSaves(int saves) {
    validateNonNegative(saves, "Saves");
    this.saves = saves;
  }

  public int getSignups() {
    return signups;
  }

  public void setSignups(int signups) {
    validateNonNegative(signups, "Signups");
    this.signups = signups;
  }

  public int getProfileToSiteClicks() {
    return profileToSiteClicks;
  }

  public void setProfileToSiteClicks(int profileToSiteClicks) {
    validateNonNegative(profileToSiteClicks, "Profile-to-site clicks");

    this.profileToSiteClicks = profileToSiteClicks;
  }

  private void validateNonNegative(int value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must not be negative");
    }
  }
}
