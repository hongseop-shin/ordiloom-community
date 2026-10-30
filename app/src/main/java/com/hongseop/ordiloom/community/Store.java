package com.hongseop.ordiloom.community;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** All authoritative state is local. Order completion and stock changes commit together. */
public final class Store extends SQLiteOpenHelper {
  static final String[] TABLES = {
    "customers", "products", "orders", "order_items", "movements", "settings"
  };
  static final String[] CREATE_SQL = {
    "CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, company TEXT"
        + " NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '',"
        + " billing TEXT NOT NULL DEFAULT '', shipping TEXT NOT NULL DEFAULT '', notes TEXT NOT"
        + " NULL DEFAULT '', archived INTEGER NOT NULL DEFAULT 0 CHECK(archived IN(0,1)))",
    "CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, sku TEXT NOT NULL COLLATE NOCASE"
        + " UNIQUE, name TEXT NOT NULL, color TEXT NOT NULL DEFAULT '', sizes TEXT NOT NULL, ratios"
        + " TEXT NOT NULL, pack_size INTEGER NOT NULL CHECK(pack_size>0), price INTEGER NOT NULL"
        + " CHECK(price>=0), stock INTEGER NOT NULL CHECK(stock>=0), archived INTEGER NOT NULL"
        + " DEFAULT 0 CHECK(archived IN(0,1)))",
    "CREATE TABLE orders (id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER REFERENCES"
        + " customers(id), customer_snapshot TEXT NOT NULL DEFAULT '{}', status TEXT NOT NULL"
        + " DEFAULT 'DRAFT' CHECK(status IN('DRAFT','COMPLETE','CANCELLED')), created INTEGER NOT"
        + " NULL, updated INTEGER NOT NULL, discount INTEGER NOT NULL DEFAULT 0 CHECK(discount>=0),"
        + " paid INTEGER NOT NULL DEFAULT 0 CHECK(paid>=0), payment TEXT NOT NULL DEFAULT 'Unpaid',"
        + " notes TEXT NOT NULL DEFAULT '', signature TEXT NOT NULL DEFAULT '', total INTEGER NOT"
        + " NULL DEFAULT 0 CHECK(total>=0))",
    "CREATE TABLE order_items (id INTEGER PRIMARY KEY AUTOINCREMENT, order_id INTEGER NOT NULL"
        + " REFERENCES orders(id) ON DELETE CASCADE, product_id INTEGER NOT NULL REFERENCES"
        + " products(id), sku TEXT NOT NULL, name TEXT NOT NULL, color TEXT NOT NULL DEFAULT '',"
        + " sizes TEXT NOT NULL, ratios TEXT NOT NULL, pack_size INTEGER NOT NULL"
        + " CHECK(pack_size>0), packs INTEGER NOT NULL CHECK(packs>0), price INTEGER NOT NULL"
        + " CHECK(price>=0), discount INTEGER NOT NULL DEFAULT 0 CHECK(discount>=0 AND"
        + " discount<=price), UNIQUE(order_id,product_id))",
    "CREATE TABLE movements (id INTEGER PRIMARY KEY AUTOINCREMENT, product_id INTEGER NOT NULL"
        + " REFERENCES products(id), order_id INTEGER REFERENCES orders(id), delta INTEGER NOT"
        + " NULL, reason TEXT NOT NULL, created INTEGER NOT NULL)",
    "CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK(id=1), organization TEXT NOT NULL, policy"
        + " TEXT NOT NULL)"
  };

  public Store(Context c) {
    this(c, "orderlab.db");
  }

  public Store(Context c, String databaseName) {
    super(c, databaseName, null, 1);
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    for (String sql : CREATE_SQL) db.execSQL(sql);
    db.execSQL("CREATE INDEX order_status ON orders(status,updated)");
    db.execSQL("CREATE INDEX item_order ON order_items(order_id)");
    db.execSQL("CREATE INDEX movement_product ON movements(product_id,id)");
    db.execSQL("INSERT INTO settings(id,organization,policy) VALUES(1,'ORDILOOM','')");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    throw new IllegalStateException(
        "A non-destructive migration is required for this database version.");
  }

  static JSONObject row(Cursor c) throws Exception {
    JSONObject o = new JSONObject();
    for (int i = 0; i < c.getColumnCount(); i++) {
      if (c.isNull(i)) o.put(c.getColumnName(i), JSONObject.NULL);
      else if (c.getType(i) == Cursor.FIELD_TYPE_INTEGER) o.put(c.getColumnName(i), c.getLong(i));
      else o.put(c.getColumnName(i), c.getString(i));
    }
    return o;
  }

  public JSONArray query(String sql, String... args) throws Exception {
    JSONArray a = new JSONArray();
    Cursor c = getReadableDatabase().rawQuery(sql, args);
    try {
      while (c.moveToNext()) a.put(row(c));
    } finally {
      c.close();
    }
    return a;
  }

  public JSONObject one(String table, long id) throws Exception {
    boolean known = false;
    for (String t : TABLES) if (t.equals(table)) known = true;
    if (!known) throw new IllegalArgumentException("table");
    JSONArray a = query("SELECT * FROM " + table + " WHERE id=?", String.valueOf(id));
    if (a.length() != 1) throw new IllegalArgumentException("missing");
    return a.getJSONObject(0);
  }

  public JSONArray products(String search, boolean archived) throws Exception {
    return query(
        "SELECT * FROM products WHERE archived=? AND (instr(lower(sku),lower(?))>0 OR"
            + " instr(lower(name),lower(?))>0 OR instr(lower(color),lower(?))>0) ORDER BY name,sku",
        archived ? "1" : "0",
        search,
        search,
        search);
  }

  public JSONArray customers(String search, boolean archived) throws Exception {
    return query(
        "SELECT * FROM customers WHERE archived=? AND (instr(lower(name),lower(?))>0 OR"
            + " instr(lower(company),lower(?))>0 OR instr(phone,?)>0 OR"
            + " instr(lower(email),lower(?))>0) ORDER BY name",
        archived ? "1" : "0",
        search,
        search,
        search,
        search);
  }

  public JSONArray orders() throws Exception {
    return query("SELECT * FROM orders ORDER BY updated DESC,id DESC");
  }

  public JSONArray items(long order) throws Exception {
    return query("SELECT * FROM order_items WHERE order_id=? ORDER BY id", String.valueOf(order));
  }

  public JSONArray movements(long product) throws Exception {
    return query(
        "SELECT * FROM movements WHERE product_id=? ORDER BY id DESC LIMIT 30",
        String.valueOf(product));
  }

  public long subtotal(long order) throws Exception {
    long total = 0;
    JSONArray items = items(order);
    for (int i = 0; i < items.length(); i++)
      total = Money.add(total, lineTotal(items.getJSONObject(i)));
    return total;
  }

  public static long lineTotal(JSONObject item) throws Exception {
    return Rules.lineTotal(
        item.getInt("packs"),
        item.getInt("pack_size"),
        item.getLong("price"),
        item.getLong("discount"));
  }

  private void draft(long id) throws Exception {
    if (!one("orders", id).getString("status").equals("DRAFT"))
      throw new IllegalArgumentException("locked");
  }

  private void recalc(long id) throws Exception {
    JSONObject o = one("orders", id);
    long sub = subtotal(id);
    // Preserve explicit adjustments; reject changes that would make them invalid.
    long total = Rules.total(sub, o.getLong("discount"), o.getLong("paid"));
    ContentValues v = new ContentValues();
    v.put("total", total);
    v.put("updated", System.currentTimeMillis());
    getWritableDatabase().update("orders", v, "id=?", new String[] {String.valueOf(id)});
  }

  public long saveCustomer(
      long id,
      String name,
      String company,
      String phone,
      String email,
      String billing,
      String shipping,
      String notes)
      throws Exception {
    ContentValues v = new ContentValues();
    v.put("name", Rules.text(name, 150, true));
    v.put("company", Rules.text(company, 150, false));
    v.put("phone", Rules.text(phone, 60, false));
    v.put("email", Rules.text(email, 200, false));
    v.put("billing", Rules.text(billing, 1000, false));
    v.put("shipping", Rules.text(shipping, 1000, false));
    v.put("notes", Rules.text(notes, 3000, false));
    SQLiteDatabase db = getWritableDatabase();
    if (id == 0) return db.insertOrThrow("customers", null, v);
    if (db.update("customers", v, "id=?", new String[] {String.valueOf(id)}) != 1)
      throw new IllegalArgumentException("missing");
    return id;
  }

  public long saveProduct(
      long id,
      String sku,
      String name,
      String color,
      String sizes,
      String ratios,
      long price,
      int stock)
      throws Exception {
    if (stock < 0 || stock > Rules.MAX_QTY) throw new IllegalArgumentException("quantity");
    ContentValues v = new ContentValues();
    v.put("sku", Rules.text(sku, 100, true));
    v.put("name", Rules.text(name, 200, true));
    v.put("color", Rules.text(color, 100, false));
    v.put("sizes", Rules.text(sizes, 300, true));
    v.put("ratios", Rules.text(ratios, 300, true));
    v.put("pack_size", Rules.packSize(sizes, ratios));
    v.put("price", Money.check(price));
    v.put("stock", stock);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      int old = id == 0 ? 0 : one("products", id).getInt("stock");
      if (id == 0) id = db.insertOrThrow("products", null, v);
      else db.update("products", v, "id=?", new String[] {String.valueOf(id)});
      if (old != stock)
        movement(db, id, 0, stock - old, old == 0 ? "Opening / adjustment" : "Adjustment");
      db.setTransactionSuccessful();
      return id;
    } finally {
      db.endTransaction();
    }
  }

  public void archive(String table, long id, boolean archived) throws Exception {
    if (!table.equals("products") && !table.equals("customers"))
      throw new IllegalArgumentException("table");
    ContentValues v = new ContentValues();
    v.put("archived", archived ? 1 : 0);
    getWritableDatabase().update(table, v, "id=?", new String[] {String.valueOf(id)});
  }

  private void movement(SQLiteDatabase db, long product, long order, int delta, String reason) {
    ContentValues v = new ContentValues();
    v.put("product_id", product);
    if (order > 0) v.put("order_id", order);
    v.put("delta", delta);
    v.put("reason", reason);
    v.put("created", System.currentTimeMillis());
    db.insertOrThrow("movements", null, v);
  }

  public long newOrder(long customer) throws Exception {
    ContentValues v = new ContentValues();
    long now = System.currentTimeMillis();
    v.put("created", now);
    v.put("updated", now);
    if (customer > 0) {
      JSONObject c = one("customers", customer);
      if (c.getInt("archived") != 0) throw new IllegalArgumentException("archived");
      v.put("customer_id", customer);
      v.put("customer_snapshot", c.toString());
    }
    return getWritableDatabase().insertOrThrow("orders", null, v);
  }

  public void setCustomer(long order, long customer) throws Exception {
    draft(order);
    JSONObject c = one("customers", customer);
    if (c.getInt("archived") != 0) throw new IllegalArgumentException("archived");
    ContentValues v = new ContentValues();
    v.put("customer_id", customer);
    v.put("customer_snapshot", c.toString());
    v.put("updated", System.currentTimeMillis());
    getWritableDatabase().update("orders", v, "id=?", new String[] {String.valueOf(order)});
  }

  public void addProduct(long order, long product) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      draft(order);
      JSONObject p = one("products", product);
      if (p.getInt("archived") != 0) throw new IllegalArgumentException("archived");
      JSONArray existing =
          query(
              "SELECT * FROM order_items WHERE order_id=? AND product_id=?",
              String.valueOf(order),
              String.valueOf(product));
      if (existing.length() > 0) {
        JSONObject line = existing.getJSONObject(0);
        updateLineInternal(db, line, line.getInt("packs") + 1, line.getLong("discount"));
      } else {
        ContentValues v = new ContentValues();
        v.put("order_id", order);
        v.put("product_id", product);
        for (String field : new String[] {"sku", "name", "color", "sizes", "ratios"})
          v.put(field, p.getString(field));
        v.put("pack_size", p.getInt("pack_size"));
        v.put("packs", 1);
        v.put("price", p.getLong("price"));
        db.insertOrThrow("order_items", null, v);
      }
      recalc(order);
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private void updateLineInternal(SQLiteDatabase db, JSONObject line, int packs, long discount)
      throws Exception {
    Rules.lineTotal(packs, line.getInt("pack_size"), line.getLong("price"), discount);
    ContentValues v = new ContentValues();
    v.put("packs", packs);
    v.put("discount", discount);
    db.update("order_items", v, "id=?", new String[] {String.valueOf(line.getLong("id"))});
  }

  public void updateLine(long id, int packs, long discount) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject line = one("order_items", id);
      draft(line.getLong("order_id"));
      updateLineInternal(db, line, packs, discount);
      recalc(line.getLong("order_id"));
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void removeLine(long id) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject l = one("order_items", id);
      draft(l.getLong("order_id"));
      db.delete("order_items", "id=?", new String[] {String.valueOf(id)});
      recalc(l.getLong("order_id"));
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void saveDetails(long id, long discount, long paid, String payment, String notes)
      throws Exception {
    draft(id);
    Rules.total(subtotal(id), discount, paid);
    ContentValues v = new ContentValues();
    v.put("discount", discount);
    v.put("paid", paid);
    v.put("payment", Rules.text(payment, 50, true));
    v.put("notes", Rules.text(notes, 5000, false));
    v.put("total", subtotal(id) - discount);
    v.put("updated", System.currentTimeMillis());
    getWritableDatabase().update("orders", v, "id=?", new String[] {String.valueOf(id)});
  }

  public void signature(long id, String png) throws Exception {
    draft(id);
    validateSignature(png);
    ContentValues v = new ContentValues();
    v.put("signature", png);
    v.put("updated", System.currentTimeMillis());
    getWritableDatabase().update("orders", v, "id=?", new String[] {String.valueOf(id)});
  }

  public void complete(long id) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      draft(id);
      JSONObject o = one("orders", id);
      JSONArray lines = items(id);
      if (o.isNull("customer_id") || lines.length() == 0)
        throw new IllegalArgumentException("incomplete");
      JSONObject customer = one("customers", o.getLong("customer_id"));
      if (customer.getInt("archived") != 0) throw new IllegalArgumentException("archived");
      recalc(id);
      for (int i = 0; i < lines.length(); i++) {
        JSONObject l = lines.getJSONObject(i), p = one("products", l.getLong("product_id"));
        if (p.getInt("archived") != 0) throw new IllegalArgumentException("archived");
        int qty = Rules.units(l.getInt("packs"), l.getInt("pack_size"));
        if (p.getInt("stock") < qty)
          throw new IllegalArgumentException("stock:" + l.getString("sku"));
        ContentValues v = new ContentValues();
        v.put("stock", p.getInt("stock") - qty);
        db.update("products", v, "id=?", new String[] {String.valueOf(p.getLong("id"))});
        movement(db, p.getLong("id"), id, -qty, "Order completed");
      }
      ContentValues v = new ContentValues();
      v.put("status", "COMPLETE");
      v.put("updated", System.currentTimeMillis());
      db.update("orders", v, "id=? AND status='DRAFT'", new String[] {String.valueOf(id)});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void cancel(long id) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject o = one("orders", id);
      String status = o.getString("status");
      if (status.equals("CANCELLED")) throw new IllegalArgumentException("locked");
      if (status.equals("COMPLETE")) {
        JSONArray lines = items(id);
        for (int i = 0; i < lines.length(); i++) {
          JSONObject l = lines.getJSONObject(i), p = one("products", l.getLong("product_id"));
          int qty = Rules.units(l.getInt("packs"), l.getInt("pack_size"));
          int stock = p.getInt("stock") + qty;
          if (stock > Rules.MAX_QTY) throw new IllegalArgumentException("quantity");
          ContentValues v = new ContentValues();
          v.put("stock", stock);
          db.update("products", v, "id=?", new String[] {String.valueOf(p.getLong("id"))});
          movement(db, p.getLong("id"), id, qty, "Order cancelled");
        }
      }
      ContentValues v = new ContentValues();
      v.put("status", "CANCELLED");
      v.put("updated", System.currentTimeMillis());
      db.update("orders", v, "id=?", new String[] {String.valueOf(id)});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public long duplicate(long original) throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject o = one("orders", original);
      long id = newOrder(o.optLong("customer_id", 0));
      JSONArray items = items(original);
      for (int i = 0; i < items.length(); i++) {
        JSONObject l = items.getJSONObject(i), p = one("products", l.getLong("product_id"));
        if (p.getInt("archived") != 0) throw new IllegalArgumentException("archived");
        addProduct(id, p.getLong("id"));
        JSONArray a =
            query(
                "SELECT * FROM order_items WHERE order_id=? AND product_id=?",
                String.valueOf(id),
                String.valueOf(p.getLong("id")));
        updateLineInternal(db, a.getJSONObject(0), l.getInt("packs"), 0);
      }
      recalc(id);
      db.setTransactionSuccessful();
      return id;
    } finally {
      db.endTransaction();
    }
  }

  public void settings(String name, String policy) throws Exception {
    ContentValues v = new ContentValues();
    v.put("organization", Rules.text(name, 150, true));
    v.put("policy", Rules.text(policy, 3000, false));
    getWritableDatabase().update("settings", v, "id=1", null);
  }

  public JSONObject backup() throws Exception {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      JSONObject out = new JSONObject();
      out.put("format", "orderlab-backup");
      out.put("version", 1);
      out.put("created", System.currentTimeMillis());
      for (String table : TABLES) out.put(table, query("SELECT * FROM " + table + " ORDER BY id"));
      db.setTransactionSuccessful();
      return out;
    } finally {
      db.endTransaction();
    }
  }

  static void validateSignature(String value) throws Exception {
    if (value.isEmpty()) return;
    if (value.length() > 350000) throw new IllegalArgumentException("backup");
    byte[] b = Base64.decode(value, Base64.DEFAULT);
    if (b.length < 24
        || b.length > 262144
        || b[0] != (byte) 137
        || b[1] != 80
        || b[2] != 78
        || b[3] != 71) throw new IllegalArgumentException("backup");
    android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
    opts.inJustDecodeBounds = true;
    android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length, opts);
    if (opts.outWidth < 1 || opts.outHeight < 1 || opts.outWidth > 2048 || opts.outHeight > 2048)
      throw new IllegalArgumentException("backup");
  }

  /** Validation runs before any write; restoration is an all-or-nothing transaction. */
  public static void validateBackup(JSONObject root) throws Exception {
    if (!root.getString("format").equals("orderlab-backup")
        || !(root.get("version") instanceof Integer)
        || root.getInt("version") != 1) throw new IllegalArgumentException("backup");
    Map<String, Set<Long>> ids = new HashMap<>();
    Map<Long, JSONObject> orderMap = new HashMap<>();
    Map<Long, Long> subtotals = new HashMap<>(), stockTotals = new HashMap<>();
    Map<Long, Integer> stocks = new HashMap<>();
    Set<String> skus = new HashSet<>(), pairs = new HashSet<>();
    for (String table : TABLES) {
      JSONArray a = root.getJSONArray(table);
      if (a.length() > 50000) throw new IllegalArgumentException("backup");
      Set<Long> set = new HashSet<>();
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        strictTypes(table, o);
        long id = o.getLong("id");
        if (id <= 0 || id > 1000000000 || !set.add(id))
          throw new IllegalArgumentException("backup");
      }
      ids.put(table, set);
    }
    JSONArray a = root.getJSONArray("customers");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      Rules.text(o.getString("name"), 150, true);
      for (String key : new String[] {"company", "phone", "email", "billing", "shipping", "notes"})
        Rules.text(o.getString(key), 3000, false);
      flag(o, "archived");
    }
    a = root.getJSONArray("products");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      String sku = Rules.text(o.getString("sku"), 100, true);
      if (!skus.add(sku.toLowerCase(java.util.Locale.ROOT)))
        throw new IllegalArgumentException("backup");
      Rules.text(o.getString("name"), 200, true);
      Rules.text(o.getString("color"), 100, false);
      Money.check(o.getLong("price"));
      qty(o, "stock", true);
      qty(o, "pack_size", false);
      if (Rules.packSize(o.getString("sizes"), o.getString("ratios")) != o.getInt("pack_size"))
        throw new IllegalArgumentException("backup");
      flag(o, "archived");
      stocks.put(o.getLong("id"), o.getInt("stock"));
    }
    a = root.getJSONArray("orders");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      String s = o.getString("status");
      if (!s.equals("DRAFT") && !s.equals("COMPLETE") && !s.equals("CANCELLED"))
        throw new IllegalArgumentException("backup");
      if (!o.isNull("customer_id") && !ids.get("customers").contains(o.getLong("customer_id")))
        throw new IllegalArgumentException("backup");
      JSONObject snapshot = new JSONObject(o.getString("customer_snapshot"));
      if (s.equals("COMPLETE")
          && (o.isNull("customer_id") || snapshot.optString("name", "").isEmpty()))
        throw new IllegalArgumentException("backup");
      Rules.text(o.getString("payment"), 50, true);
      Rules.text(o.getString("notes"), 5000, false);
      Money.check(o.getLong("discount"));
      Money.check(o.getLong("paid"));
      Money.check(o.getLong("total"));
      if (o.getLong("created") < 0 || o.getLong("updated") < 0)
        throw new IllegalArgumentException("backup");
      validateSignature(o.getString("signature"));
      orderMap.put(o.getLong("id"), o);
    }
    a = root.getJSONArray("order_items");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      long order = o.getLong("order_id"), product = o.getLong("product_id");
      if (!ids.get("orders").contains(order)
          || !ids.get("products").contains(product)
          || !pairs.add(order + ":" + product)) throw new IllegalArgumentException("backup");
      for (String key : new String[] {"sku", "name", "color", "sizes", "ratios"})
        Rules.text(o.getString(key), 300, !key.equals("color"));
      qty(o, "packs", false);
      qty(o, "pack_size", false);
      if (Rules.packSize(o.getString("sizes"), o.getString("ratios")) != o.getInt("pack_size"))
        throw new IllegalArgumentException("backup");
      long before = subtotals.containsKey(order) ? subtotals.get(order) : 0;
      subtotals.put(order, Money.add(before, lineTotal(o)));
    }
    for (Map.Entry<Long, JSONObject> e : orderMap.entrySet()) {
      JSONObject o = e.getValue();
      long sub = subtotals.containsKey(e.getKey()) ? subtotals.get(e.getKey()) : 0;
      if (o.getString("status").equals("COMPLETE") && !subtotals.containsKey(e.getKey()))
        throw new IllegalArgumentException("backup");
      if (Rules.total(sub, o.getLong("discount"), o.getLong("paid")) != o.getLong("total"))
        throw new IllegalArgumentException("backup");
    }
    a = root.getJSONArray("movements");
    for (int i = 0; i < a.length(); i++) {
      JSONObject o = a.getJSONObject(i);
      long product = o.getLong("product_id");
      if (!ids.get("products").contains(product)
          || (!o.isNull("order_id") && !ids.get("orders").contains(o.getLong("order_id"))))
        throw new IllegalArgumentException("backup");
      long delta = o.getLong("delta");
      if (delta < -Rules.MAX_QTY || delta > Rules.MAX_QTY || o.getLong("created") < 0)
        throw new IllegalArgumentException("backup");
      Rules.text(o.getString("reason"), 200, true);
      stockTotals.put(
          product, (stockTotals.containsKey(product) ? stockTotals.get(product) : 0) + delta);
    }
    for (Map.Entry<Long, Integer> e : stocks.entrySet())
      if ((stockTotals.containsKey(e.getKey()) ? stockTotals.get(e.getKey()) : 0)
          != e.getValue().longValue()) throw new IllegalArgumentException("backup");
    a = root.getJSONArray("settings");
    if (a.length() != 1 || a.getJSONObject(0).getLong("id") != 1)
      throw new IllegalArgumentException("backup");
    Rules.text(a.getJSONObject(0).getString("organization"), 150, true);
    Rules.text(a.getJSONObject(0).getString("policy"), 3000, false);
  }

  private static void strictTypes(String table, JSONObject o) throws Exception {
    String numeric = " id ";
    String nullable = " ";
    if (table.equals("customers")) numeric += "archived ";
    else if (table.equals("products")) numeric += "pack_size price stock archived ";
    else if (table.equals("orders")) {
      numeric += "customer_id created updated discount paid total ";
      nullable += "customer_id ";
    } else if (table.equals("order_items"))
      numeric += "order_id product_id pack_size packs price discount ";
    else if (table.equals("movements")) {
      numeric += "product_id order_id delta created ";
      nullable += "order_id ";
    }
    java.util.Iterator<String> keys = o.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = o.get(key);
      if (nullable.contains(" " + key + " ") && value == JSONObject.NULL) continue;
      if (numeric.contains(" " + key + " ")) {
        if (!(value instanceof Integer) && !(value instanceof Long))
          throw new IllegalArgumentException("backup");
      } else if (!(value instanceof String)) throw new IllegalArgumentException("backup");
    }
  }

  private static void flag(JSONObject o, String field) throws Exception {
    long v = o.getLong(field);
    if (v != 0 && v != 1) throw new IllegalArgumentException("backup");
  }

  private static void qty(JSONObject o, String field, boolean zero) throws Exception {
    long v = o.getLong(field);
    if (v < (zero ? 0 : 1) || v > Rules.MAX_QTY) throw new IllegalArgumentException("quantity");
  }

  public void restore(JSONObject root) throws Exception {
    validateBackup(root);
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (String table :
          new String[] {"movements", "order_items", "orders", "products", "customers", "settings"})
        db.delete(table, null, null);
      for (String table : TABLES) {
        JSONArray a = root.getJSONArray(table);
        Cursor schema = db.rawQuery("SELECT * FROM " + table + " LIMIT 0", null);
        String[] columns = schema.getColumnNames();
        schema.close();
        for (int i = 0; i < a.length(); i++) {
          JSONObject row = a.getJSONObject(i);
          ContentValues v = new ContentValues();
          for (String col : columns) {
            Object value = row.get(col);
            if (value == JSONObject.NULL) v.putNull(col);
            else if (value instanceof Number) v.put(col, ((Number) value).longValue());
            else v.put(col, (String) value);
          }
          db.insertOrThrow(table, null, v);
        }
      }
      Cursor check = db.rawQuery("PRAGMA foreign_key_check", null);
      try {
        if (check.moveToFirst()) throw new IllegalArgumentException("backup");
      } finally {
        check.close();
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }
}
