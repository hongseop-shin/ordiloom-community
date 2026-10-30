package com.hongseop.ordiloom.community;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Integer cents keep order totals exact across save, restore and repeated edits. */
public final class Money {
  public static final long MAX = 100000000000L;

  private Money() {}

  public static long parse(String text) {
    String value = text == null ? "" : text.trim();
    if (!value.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("money");
    try {
      return check(new BigDecimal(value).movePointRight(2).longValueExact());
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("money");
    }
  }

  public static long check(long value) {
    if (value < 0 || value > MAX) throw new IllegalArgumentException("money");
    return value;
  }

  public static long add(long a, long b) {
    check(a);
    check(b);
    if (a > MAX - b) throw new IllegalArgumentException("money");
    return a + b;
  }

  public static long multiply(long cents, int count) {
    check(cents);
    if (count < 0 || (count > 0 && cents > MAX / count))
      throw new IllegalArgumentException("money");
    return cents * count;
  }

  public static String input(long cents) {
    return BigDecimal.valueOf(cents, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  public static String format(long cents) {
    return NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2));
  }
}
