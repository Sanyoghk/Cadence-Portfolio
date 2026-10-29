package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BrandTest {

  @Test
  void shouldCreateBrandWithValidFields() {

    Brand brand = new Brand();

    brand.setName("Nike");
    brand.setWebsiteUrl("https://nike.com");
    brand.setInstagramHandle("nike");
    brand.setTiktokHandle("nike");

    assertEquals("Nike", brand.getName());
    assertEquals("https://nike.com", brand.getWebsiteUrl());
    assertEquals("nike", brand.getInstagramHandle());
    assertEquals("nike", brand.getTiktokHandle());
  }

  @Test
  void shouldUpdateSignature() {

    Brand brand = new Brand();

    ColorPalette palette = new ColorPalette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5");

    BrandSignature signature =
        new BrandSignature(
            palette,
            java.util.List.of("Inter"),
            java.util.List.of("professional"),
            java.util.List.of("spammy"),
            "Example caption");

    brand.updateSignature(signature);

    assertNotNull(brand.getSignature());
    assertEquals(signature, brand.getSignature());
  }

  @Test
  void shouldReplaceExistingSignature() {

    Brand brand = new Brand();

    BrandSignature oldSignature = new BrandSignature();
    BrandSignature newSignature = new BrandSignature();

    brand.updateSignature(oldSignature);
    brand.updateSignature(newSignature);

    assertEquals(newSignature, brand.getSignature());
  }

  @Test
  void shouldNormalizeFieldsAndProtectOwnership() {
    Workspace owner = new Workspace("Owner", "Retail");
    Brand brand = new Brand("  Cadence  ", owner);

    brand.setWebsiteUrl("  https://example.com  ");
    brand.setInstagramHandle("  cadence  ");

    assertEquals("Cadence", brand.getName());
    assertEquals("https://example.com", brand.getWebsiteUrl());
    assertEquals("cadence", brand.getInstagramHandle());
    assertThrows(
        IllegalStateException.class, () -> brand.setWorkspace(new Workspace("Other", "Retail")));
    assertThrows(IllegalArgumentException.class, () -> new Brand("Brand", null));
  }

  @Test
  void shouldArchiveOnlyOnce() {
    Brand brand = new Brand("Brand", new Workspace("Studio", "Creator"));

    brand.archive();

    assertEquals(BrandStatus.ARCHIVED, brand.getStatus());
    assertThrows(IllegalStateException.class, brand::archive);
  }
}
