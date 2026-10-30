package com.hongseop.ordiloom.community;

public final class Rules {
  public static final int MAX_QTY = 1000000;

  private Rules() {}

  public static int quantity(String s, boolean allowZero) {
    try {
      if (s == null || !s.trim().matches("[0-9]{1,7}")) throw new NumberFormatException();
      int n = Integer.parseInt(s.trim());
      if (n < (allowZero ? 0 : 1) || n > MAX_QTY) throw new NumberFormatException();
      return n;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("quantity");
    }
  }

  public static int packSize(String sizes, String ratios) {
    String[] labels = sizes.split(",", -1), counts = ratios.split(",", -1);
    if (labels.length != counts.length || labels.length > 30)
      throw new IllegalArgumentException("ratio");
    int total = 0;
    for (int i = 0; i < counts.length; i++) {
      if (labels[i].trim().isEmpty()) throw new IllegalArgumentException("ratio");
      total += quantity(counts[i], true);
      if (total > MAX_QTY) throw new IllegalArgumentException("ratio");
    }
    if (total < 1) throw new IllegalArgumentException("ratio");
    return total;
  }

  public static int units(int packs, int size) {
    if (packs < 1 || size < 1 || packs > MAX_QTY / size)
      throw new IllegalArgumentException("quantity");
    return packs * size;
  }

  public static long lineTotal(int packs, int size, long price, long discount) {
    Money.check(price);
    Money.check(discount);
    if (discount > price) throw new IllegalArgumentException("discount");
    return Money.multiply(price - discount, units(packs, size));
  }

  public static long total(long subtotal, long discount, long paid) {
    Money.check(subtotal);
    Money.check(discount);
    Money.check(paid);
    if (discount > subtotal) throw new IllegalArgumentException("discount");
    if (paid > subtotal - discount) throw new IllegalArgumentException("paid");
    return subtotal - discount;
  }

  public static String text(String s, int limit, boolean required) {
    String v = s == null ? "" : s.trim();
    if ((required && v.isEmpty()) || v.length() > limit) throw new IllegalArgumentException("text");
    return v;
  }
}
