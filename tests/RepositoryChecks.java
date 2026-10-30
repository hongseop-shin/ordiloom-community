package com.hongseop.ordiloom.community.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import com.hongseop.ordiloom.community.Store;
import org.json.JSONObject;

/** Runs against the real Android SQLite implementation in an isolated test database. */
public final class RepositoryChecks extends Instrumentation {
  interface Attempt {
    void run() throws Exception;
  }

  private int passed = 0;

  private void check(boolean b, String label) {
    if (!b) throw new AssertionError(label);
    passed++;
  }

  private void rejects(Attempt a, String label) throws Exception {
    try {
      a.run();
    } catch (IllegalArgumentException expected) {
      passed++;
      return;
    }
    throw new AssertionError(label);
  }

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    Store s = null;
    try {
      getTargetContext().deleteDatabase("orderlab-checks.db");
      s = new Store(getTargetContext(), "orderlab-checks.db");
      final Store db = s;
      long c =
          s.saveCustomer(
              0,
              "Sample Customer",
              "Study Studio",
              "555-0100",
              "sample@example.invalid",
              "Billing address",
              "Shipping address",
              "O'Connor");
      long p = s.saveProduct(0, "TEE-01", "Study Tee", "Blue", "S,M,L", "1,2,1", 250, 20);
      long order = s.newOrder(c);
      s.addProduct(order, p);
      check(s.subtotal(order) == 1000, "pack math");
      check(s.one("products", p).getInt("stock") == 20, "draft does not deduct stock");
      long line = s.items(order).getJSONObject(0).getLong("id");
      s.updateLine(line, 3, 25);
      s.saveDetails(order, 300, 1200, "Cash", "Local order");
      check(s.one("orders", order).getLong("total") == 2400, "discount total");
      rejects(() -> db.updateLine(line, 1, 25), "paid limit should roll back line edit");
      check(s.items(order).getJSONObject(0).getInt("packs") == 3, "failed edit rolled back");
      s.complete(order);
      check(s.one("products", p).getInt("stock") == 8, "completion deducts 12 units");
      check(s.one("orders", order).getString("status").equals("COMPLETE"), "completed status");
      rejects(() -> db.complete(order), "double completion");
      check(s.one("products", p).getInt("stock") == 8, "repeat does not deduct twice");
      JSONObject backup = s.backup();
      Store.validateBackup(backup);
      passed++;
      s.cancel(order);
      check(s.one("products", p).getInt("stock") == 20, "cancellation restores stock");
      rejects(() -> db.cancel(order), "double cancellation");
      s.restore(backup);
      check(s.one("products", p).getInt("stock") == 8, "backup restored stock");
      check(
          s.one("orders", order).getString("status").equals("COMPLETE"), "backup restored status");
      JSONObject invalid = new JSONObject(backup.toString());
      invalid.getJSONArray("orders").getJSONObject(0).put("total", 1);
      rejects(() -> db.restore(invalid), "wrong totals");
      check(
          s.one("orders", order).getLong("total") == 2400, "invalid backup leaves database intact");
      JSONObject fractional = new JSONObject(backup.toString());
      fractional.getJSONArray("order_items").getJSONObject(0).put("packs", 3.5);
      rejects(() -> db.restore(fractional), "fractional pack count");
      JSONObject oversized = new JSONObject(backup.toString());
      oversized.getJSONArray("products").getJSONObject(0).put("pack_size", 4294967300L);
      rejects(() -> db.restore(oversized), "pack size cannot wrap to an int");
      JSONObject badStock = new JSONObject(backup.toString());
      badStock.getJSONArray("products").getJSONObject(0).put("stock", 123);
      rejects(() -> db.restore(badStock), "stock ledger mismatch");
      long p2 = s.saveProduct(0, "SOCK-01", "Study Socks", "Gray", "M", "2", 100, 1),
          blocked = s.newOrder(c);
      s.addProduct(blocked, p);
      s.addProduct(blocked, p2);
      rejects(() -> db.complete(blocked), "insufficient stock");
      check(s.one("products", p).getInt("stock") == 8, "partial deduction rolled back");
      check(
          s.one("orders", blocked).getString("status").equals("DRAFT"),
          "failed order kept as draft");
      s.saveProduct(p, "TEE-01", "Study Tee", "Blue", "S,M,L", "1,2,1", 300, 8);
      long copy = s.duplicate(order);
      check(s.subtotal(copy) == 3600, "duplicate uses current catalog price");
      check(s.one("orders", copy).getLong("paid") == 0, "duplicate clears payment");
      check(s.one("orders", copy).getLong("discount") == 0, "duplicate clears order discount");
      s.archive("products", p, true);
      rejects(() -> db.complete(copy), "archived product");
      s.archive("products", p, false);
      Store.validateBackup(s.backup());
      passed++;
      java.io.ByteArrayOutputStream pdf = new java.io.ByteArrayOutputStream();
      new com.hongseop.ordiloom.community.Receipt().write(s, order, pdf);
      check(new String(pdf.toByteArray(), 0, 4, "US-ASCII").equals("%PDF"), "PDF generation");
      result.putString("stream", "PASS: " + passed + " Android repository checks.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      java.io.StringWriter writer = new java.io.StringWriter();
      e.printStackTrace(new java.io.PrintWriter(writer));
      result.putString("stream", "FAIL after " + passed + " checks\n" + writer.toString());
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (s != null) s.close();
      getTargetContext().deleteDatabase("orderlab-checks.db");
    }
  }
}
