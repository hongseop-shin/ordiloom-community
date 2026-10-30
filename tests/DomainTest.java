import com.hongseop.ordiloom.community.Money;
import com.hongseop.ordiloom.community.Rules;

public final class DomainTest {
  private static int assertions = 0;

  interface Check {
    void run();
  }

  private static void equal(long expected, long actual) {
    assertions++;
    if (expected != actual) throw new AssertionError(expected + " != " + actual);
  }

  private static void rejects(Check c) {
    assertions++;
    try {
      c.run();
    } catch (IllegalArgumentException expected) {
      return;
    }
    throw new AssertionError("Invalid input was accepted");
  }

  public static void main(String[] args) {
    equal(10, Money.parse("0.10"));
    equal(20, Money.parse("0.20"));
    equal(30, Money.add(Money.parse("0.10"), Money.parse("0.20")));
    equal(100, Money.parse("1"));
    equal(101, Money.parse("1.01"));
    equal(0, Money.parse("0.00"));
    equal(100000000000L, Money.parse("1000000000"));
    rejects(() -> Money.parse("-1"));
    rejects(() -> Money.parse("1.001"));
    rejects(() -> Money.parse("NaN"));
    rejects(() -> Money.parse("Infinity"));
    rejects(() -> Money.parse("1e5"));
    rejects(() -> Money.parse(""));
    rejects(() -> Money.parse("."));
    rejects(() -> Money.parse("1,23"));
    rejects(() -> Money.parse("1000000001"));
    rejects(() -> Money.add(Money.MAX, 1));
    rejects(() -> Money.multiply(Money.MAX, 2));
    rejects(() -> Money.multiply(100, -1));
    equal(4, Rules.packSize("S,M,L", "1,2,1"));
    equal(2, Rules.packSize("S,M,L", "0,2,0"));
    rejects(() -> Rules.packSize("S,M", "1"));
    rejects(() -> Rules.packSize("S,,L", "1,2,1"));
    rejects(() -> Rules.packSize("S,M", "0,0"));
    rejects(() -> Rules.packSize("S,M", "1,-1"));
    equal(12, Rules.units(3, 4));
    equal(1000000, Rules.units(1000, 1000));
    rejects(() -> Rules.units(1001, 1000));
    rejects(() -> Rules.units(0, 4));
    equal(2700, Rules.lineTotal(3, 4, 250, 25));
    equal(0, Rules.lineTotal(3, 4, 250, 250));
    rejects(() -> Rules.lineTotal(3, 4, 250, 251));
    equal(2400, Rules.total(2700, 300, 1200));
    equal(0, Rules.total(2700, 2700, 0));
    rejects(() -> Rules.total(2700, 2701, 0));
    rejects(() -> Rules.total(2700, 300, 2401));
    equal(0, Rules.quantity("0", true));
    equal(1, Rules.quantity("1", false));
    rejects(() -> Rules.quantity("0", false));
    rejects(() -> Rules.quantity("1.5", true));
    rejects(() -> Rules.quantity("1000001", true));
    rejects(() -> Rules.text("  ", 10, true));
    rejects(() -> Rules.text("12345", 4, false));
    System.out.println(
        "PASS: "
            + assertions
            + " domain assertions (money, pack quantities, discounts, payment limits).");
  }
}
