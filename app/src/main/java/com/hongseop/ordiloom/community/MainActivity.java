package com.hongseop.ordiloom.community;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Standard Android UI and storage only; no login, network client or device SDK. */
public final class MainActivity extends Activity {
  private static final int INK = 0xff102c36,
      TEAL = 0xff147a72,
      MUTED = 0xff5d737b,
      BG = 0xfff4f7f6,
      LINE = 0xffdce6e3;
  private static final int EXPORT = 101, IMPORT = 102;
  private Store store;
  private SharedPreferences prefs;
  private LinearLayout root, body;
  private String screen = "home";
  private long orderId = 0;
  private boolean korean, busy;

  interface Action {
    void run() throws Exception;
  }

  interface IdAction {
    void run(long id) throws Exception;
  }

  interface Job {
    Object run(Store s) throws Exception;
  }

  interface Result {
    void run(Object value) throws Exception;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new Store(getApplicationContext());
    prefs = getSharedPreferences("preferences", MODE_PRIVATE);
    if (state != null) {
      screen = state.getString("screen", "home");
      orderId = state.getLong("order");
    }
    guard(() -> render());
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putString("screen", screen);
    out.putLong("order", orderId);
  }

  @Override
  protected void onDestroy() {
    store.close();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (busy) return;
    if (screen.equals("home")) super.onBackPressed();
    else
      guard(
          () -> {
            screen = screen.equals("order") ? "orders" : "home";
            render();
          });
  }

  private String t(String en, String ko) {
    return korean ? ko : en;
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private void guard(Action action) {
    try {
      action.run();
    } catch (Exception e) {
      error(e);
    }
  }

  private void error(Exception e) {
    String key = e.getMessage() == null ? "" : e.getMessage(), msg;
    if (key.startsWith("stock:"))
      msg = t("Not enough stock for ", "재고가 부족합니다: ") + key.substring(6);
    else if (key.equals("money"))
      msg =
          t(
              "Enter an amount from 0 to 1,000,000,000 with up to two decimal places.",
              "금액은 0~1,000,000,000 사이에서 소수점 둘째 자리까지 입력해 주세요.");
    else if (key.equals("quantity") || key.equals("ratio"))
      msg =
          t(
              "Check quantities and size ratios. Size labels and ratios must match; total units"
                  + " must be 1–1,000,000.",
              "수량과 사이즈 구성비를 확인해 주세요. 사이즈와 비율의 개수가 같아야 하며 총수량은 1~1,000,000개여야 합니다.");
    else if (key.equals("discount") || key.equals("paid"))
      msg =
          t(
              "The discount cannot exceed the price or subtotal. Paid amount cannot exceed the"
                  + " total. Adjust order totals before reducing lines.",
              "할인은 단가·소계보다 클 수 없고, 지불액은 총액보다 클 수 없습니다. 품목 수량을 줄이기 전에 할인·지불액을 조정해 주세요.");
    else if (key.equals("incomplete"))
      msg = t("Select a customer and add at least one item.", "고객을 선택하고 상품을 한 개 이상 추가해 주세요.");
    else if (key.equals("archived"))
      msg =
          t(
              "A selected customer or product is archived. Restore it before using this order.",
              "선택한 고객 또는 상품이 보관 처리되었습니다. 먼저 복원해 주세요.");
    else if (key.equals("locked"))
      msg =
          t(
              "This order is no longer a draft. Duplicate it to create a new order.",
              "이 주문은 편집할 수 없습니다. 복제하여 새 주문을 만들어 주세요.");
    else if (key.equals("signature"))
      msg = t("Draw a signature before saving.", "서명을 작성한 뒤 저장해 주세요.");
    else if (e instanceof android.database.sqlite.SQLiteConstraintException)
      msg =
          t(
              "This SKU already exists, or the change would break a data link.",
              "이미 존재하는 SKU이거나 데이터 연결 조건에 맞지 않는 변경입니다.");
    else if (key.equals("text"))
      msg =
          t(
              "Complete required fields and shorten any unusually long text.",
              "필수 항목을 입력하고 너무 긴 내용을 줄여 주세요.");
    else if (key.equals("missing"))
      msg =
          t(
              "This record is not available. Return to the list and try again.",
              "자료를 찾을 수 없습니다. 목록으로 돌아가 다시 선택해 주세요.");
    else
      msg =
          t(
              "The operation could not be completed. Check the selected file, input, and available"
                  + " storage. Existing records remain available.",
              "작업을 완료하지 못했습니다. 선택한 파일, 입력값, 저장 공간을 확인해 주세요. 기존 기록은 유지됩니다.");
    if (!isFinishing() && !isDestroyed())
      new AlertDialog.Builder(this)
          .setTitle(t("Please check", "확인해 주세요"))
          .setMessage(msg)
          .setPositiveButton(t("OK", "확인"), null)
          .show();
  }

  private void toast(String en, String ko) {
    Toast.makeText(this, t(en, ko), Toast.LENGTH_SHORT).show();
  }

  private GradientDrawable background(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(14));
    return d;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private TextView text(String s, int size, int color, boolean bold) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setLineSpacing(dp(3), 1);
    if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    return v;
  }

  private void addText(LinearLayout p, String s, int size, int color, boolean bold) {
    TextView v = text(s, size, color, bold);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(8);
    p.addView(v, lp);
  }

  private Button button(String label, boolean primary, Action action) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(primary ? Color.WHITE : TEAL);
    b.setBackground(background(primary ? TEAL : 0xffe8f2ef));
    b.setPadding(dp(14), dp(11), dp(14), dp(11));
    b.setMinHeight(dp(48));
    b.setOnClickListener(v -> guard(action));
    return b;
  }

  private void addButton(LinearLayout p, String label, boolean primary, Action action) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(6);
    lp.bottomMargin = dp(6);
    p.addView(button(label, primary, action), lp);
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = column();
    c.setPadding(dp(18), dp(18), dp(18), dp(14));
    c.setBackground(background(Color.WHITE));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(12);
    parent.addView(c, lp);
    return c;
  }

  private EditText field(LinearLayout p, String label, String value, int type, int lines) {
    addText(p, label, 12, MUTED, true);
    EditText e = new EditText(this);
    e.setText(value);
    e.setTextSize(16);
    e.setTextColor(INK);
    e.setInputType(type);
    e.setSingleLine(lines == 1);
    e.setMinLines(lines);
    e.setMaxLines(Math.max(lines, 5));
    e.setPadding(dp(8), dp(6), dp(8), dp(6));
    e.setContentDescription(label);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(12);
    p.addView(e, lp);
    return e;
  }

  private EditText input(LinearLayout p, String label, String value) {
    return field(p, label, value, InputType.TYPE_CLASS_TEXT, 1);
  }

  private EditText money(LinearLayout p, String label, long value) {
    return field(
        p,
        label,
        Money.input(value),
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL,
        1);
  }

  private EditText qty(LinearLayout p, String label, int value) {
    return field(p, label, String.valueOf(value), InputType.TYPE_CLASS_NUMBER, 1);
  }

  private EditText multiline(LinearLayout p, String label, String value) {
    return field(
        p, label, value, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, 3);
  }

  private void watch(EditText e, Action action) {
    e.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            guard(action);
          }

          public void afterTextChanged(Editable s) {}
        });
  }

  private void dialog(String title, LinearLayout form, String save, Action action) {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    form.setPadding(dp(18), dp(12), dp(18), dp(10));
    scroll.addView(form);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setNegativeButton(t("Close", "닫기"), null)
            .setPositiveButton(save, null)
            .create();
    form.setTag(d);
    d.setOnShowListener(
        v ->
            d.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    x -> {
                      try {
                        action.run();
                        d.dismiss();
                        render();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    d.show();
    d.getWindow()
        .setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  }

  private void closeForm(LinearLayout form) {
    Object dialog = form.getTag();
    if (dialog instanceof AlertDialog) ((AlertDialog) dialog).dismiss();
  }

  private void confirm(String message, Action action) {
    new AlertDialog.Builder(this)
        .setTitle(t("Confirm", "확인"))
        .setMessage(message)
        .setNegativeButton(t("Back", "돌아가기"), null)
        .setPositiveButton(t("Continue", "계속"), (d, w) -> guard(action))
        .show();
  }

  private void go(String page) throws Exception {
    screen = page;
    render();
  }

  private String date(long n) {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(n));
  }

  private String status(String s) {
    return s.equals("DRAFT")
        ? t("Draft", "작성 중")
        : s.equals("COMPLETE") ? t("Complete", "완료") : t("Cancelled", "취소");
  }

  private String number(long id) {
    return String.format(Locale.US, "#%05d", id);
  }

  private void render() throws Exception {
    String lang = prefs.getString("language", "system");
    korean =
        lang.equals("ko")
            || (lang.equals("system")
                && getResources().getConfiguration().locale.getLanguage().equals("ko"));
    root = column();
    root.setBackgroundColor(BG);
    root.setFitsSystemWindows(true);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          v.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              insets.getSystemWindowInsetBottom());
          return insets;
        });
    setContentView(root);
    root.requestApplyInsets();
    LinearLayout header = column();
    header.setPadding(dp(22), dp(17), dp(22), dp(12));
    header.setBackgroundColor(INK);
    root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    addText(header, "ORDILOOM Community", 25, Color.WHITE, true);
    addText(header, t("Your orders. On your device.", "고객과 주문을 내 기기에서."), 12, 0xffb8d4cd, false);
    ScrollView scroller = new ScrollView(this);
    scroller.setFillViewport(true);
    root.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
    FrameLayout frame = new FrameLayout(this);
    scroller.addView(frame);
    body = column();
    body.setPadding(dp(16), dp(22), dp(16), dp(20));
    FrameLayout.LayoutParams content =
        new FrameLayout.LayoutParams(
            Math.min(getResources().getDisplayMetrics().widthPixels, dp(820)),
            -2,
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    frame.addView(body, content);
    if (screen.equals("home")) home();
    else if (screen.equals("products")) productPage();
    else if (screen.equals("customers")) customerPage();
    else if (screen.equals("orders")) orderPage();
    else if (screen.equals("order")) orderEditor();
    else settingsPage();
    LinearLayout nav = new LinearLayout(this);
    nav.setBackgroundColor(Color.WHITE);
    nav.setPadding(dp(4), dp(6), dp(4), dp(6));
    root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
    String[] keys = {"home", "orders", "products", "customers", "settings"},
        en = {"Home", "Orders", "Items", "People", "More"},
        ko = {"홈", "주문", "상품", "고객", "설정"};
    for (int i = 0; i < keys.length; i++) {
      final String target = keys[i];
      TextView b =
          text(
              t(en[i], ko[i]),
              12,
              screen.equals(target) || (target.equals("orders") && screen.equals("order"))
                  ? TEAL
                  : MUTED,
              true);
      b.setGravity(Gravity.CENTER);
      b.setPadding(0, dp(13), 0, dp(13));
      b.setMinHeight(dp(48));
      b.setOnClickListener(v -> guard(() -> go(target)));
      nav.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
  }

  private void home() throws Exception {
    addText(body, t("A clear view of your work", "한눈에 보는 주문 현황"), 25, INK, true);
    JSONObject counts =
        store
            .query(
                "SELECT (SELECT count(*) FROM products WHERE archived=0) AS products,(SELECT"
                    + " count(*) FROM customers WHERE archived=0) AS customers,(SELECT count(*)"
                    + " FROM orders WHERE status='DRAFT') AS drafts,(SELECT coalesce(sum(total),0)"
                    + " FROM orders WHERE status='COMPLETE') AS total")
            .getJSONObject(0);
    LinearLayout hero = card(body);
    addText(hero, t("COMPLETED ORDER VALUE", "완료 주문 금액"), 11, MUTED, true);
    addText(hero, Money.format(counts.getLong("total")), 32, INK, true);
    addText(
        hero,
        t("USD · Payment amounts are records only.", "USD 기준 · 지불액은 기록용입니다."),
        12,
        MUTED,
        false);
    addText(
        hero,
        counts.getLong("products")
            + t(" items  ·  ", "개 상품  ·  ")
            + counts.getLong("customers")
            + t(" customers  ·  ", "명 고객  ·  ")
            + counts.getLong("drafts")
            + t(" drafts", "건 작성 중"),
        14,
        INK,
        false);
    addButton(
        body,
        t("+ Create an order", "+ 새 주문 만들기"),
        true,
        () -> {
          orderId = store.newOrder(0);
          go("order");
        });
    if (counts.getLong("products") == 0 || counts.getLong("customers") == 0) {
      LinearLayout start = card(body);
      addText(start, t("Start with your catalog", "첫 자료를 등록해 주세요"), 18, INK, true);
      addText(
          start,
          t(
              "Add a product and a customer, then create your first order. Drafts are saved as you"
                  + " work.",
              "상품과 고객을 등록한 뒤 첫 주문을 만들어 보세요. 주문 초안은 편집할 때마다 저장됩니다."),
          14,
          MUTED,
          false);
      addButton(start, t("Add a product", "상품 등록"), false, () -> productForm(0, null));
      addButton(start, t("Add a customer", "고객 등록"), false, () -> customerForm(0, null));
    }
    LinearLayout recent = card(body);
    addText(recent, t("Recent orders", "최근 주문"), 18, INK, true);
    JSONArray orders = store.orders();
    if (orders.length() == 0)
      addText(
          recent,
          t("Your order history will appear here.", "주문을 만들면 여기에 표시됩니다."),
          14,
          MUTED,
          false);
    for (int i = 0; i < Math.min(4, orders.length()); i++)
      orderRow(recent, orders.getJSONObject(i));
  }

  private void productPage() throws Exception {
    addText(body, t("Product catalog", "상품 목록"), 25, INK, true);
    addButton(body, t("+ Add product", "+ 상품 등록"), true, () -> productForm(0, null));
    EditText search = input(body, t("Search SKU, name or color", "SKU·상품명·색상 검색"), "");
    CheckBox archived = new CheckBox(this);
    archived.setText(t("Show archived products", "보관한 상품 보기"));
    body.addView(archived);
    LinearLayout list = column();
    body.addView(list);
    Action refresh =
        () -> {
          list.removeAllViews();
          JSONArray a = store.products(search.getText().toString(), archived.isChecked());
          if (a.length() == 0)
            addText(list, t("No matching products.", "상품이 없습니다."), 14, MUTED, false);
          for (int i = 0; i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            long id = p.getLong("id");
            LinearLayout c = card(list);
            addText(c, p.getString("name"), 18, INK, true);
            addText(c, p.getString("sku") + "  ·  " + p.getString("color"), 13, MUTED, false);
            addText(
                c,
                Money.format(p.getLong("price"))
                    + t(" / unit   ·   ", " / 개   ·   ")
                    + p.getInt("stock")
                    + t(" in stock", "개 재고"),
                15,
                INK,
                true);
            addText(
                c,
                t("Pack: ", "팩 구성: ")
                    + p.getString("sizes")
                    + " / "
                    + p.getString("ratios")
                    + " ("
                    + p.getInt("pack_size")
                    + t(" units)", "개)"),
                12,
                MUTED,
                false);
            addButton(
                c, t("Edit / stock history", "편집 / 입출고 이력"), false, () -> productForm(id, null));
          }
        };
    watch(search, refresh);
    archived.setOnCheckedChangeListener((b, c) -> guard(refresh));
    refresh.run();
  }

  private void productForm(long id, IdAction after) throws Exception {
    JSONObject p = id > 0 ? store.one("products", id) : new JSONObject();
    LinearLayout f = column();
    EditText sku = input(f, t("SKU / barcode *", "SKU / 바코드 번호 *"), p.optString("sku")),
        name = input(f, t("Product / style name *", "상품 / 스타일 이름 *"), p.optString("name")),
        color = input(f, t("Color", "색상"), p.optString("color"));
    EditText
        sizes =
            input(
                f,
                t("Size labels (comma separated)", "사이즈 목록 (쉼표로 구분)"),
                p.optString("sizes", "One size")),
        ratios =
            input(
                f,
                t("Units per size (e.g. 1,2,1)", "사이즈별 구성 수량 (예: 1,2,1)"),
                p.optString("ratios", "1"));
    EditText price = money(f, t("Unit price (USD)", "개당 가격 (USD)"), p.optLong("price", 0)),
        stock =
            qty(f, t("On-hand stock (individual units)", "보유 재고 (개별 수량)"), p.optInt("stock", 0));
    if (id > 0) {
      addText(
          f,
          t(
              "Price changes apply to newly added order lines. Existing drafts keep their recorded"
                  + " price and pack size.",
              "가격 변경은 새로 추가하는 주문 품목에 적용됩니다. 기존 초안은 기록된 가격과 팩 구성을 유지합니다."),
          12,
          MUTED,
          false);
      boolean archived = p.getInt("archived") == 1;
      addButton(
          f,
          archived ? t("Restore product", "상품 복원") : t("Archive product", "상품 보관"),
          false,
          () ->
              confirm(
                  t(
                      "Change this product's archive status? Existing order history is kept.",
                      "상품의 보관 상태를 변경할까요? 기존 주문 이력은 유지됩니다."),
                  () -> {
                    store.archive("products", id, !archived);
                    closeForm(f);
                    render();
                    toast("Archive status changed.", "보관 상태를 변경했습니다.");
                  }));
      JSONArray moves = store.movements(id);
      addText(f, t("Latest stock movements", "최근 입출고 이력"), 14, INK, true);
      for (int i = 0; i < moves.length(); i++) {
        JSONObject m = moves.getJSONObject(i);
        addText(
            f,
            date(m.getLong("created"))
                + " · "
                + (m.getInt("delta") > 0 ? "+" : "")
                + m.getInt("delta")
                + " · "
                + m.getString("reason")
                + (m.isNull("order_id") ? "" : " " + number(m.getLong("order_id"))),
            11,
            MUTED,
            false);
      }
    }
    dialog(
        t("Product", "상품"),
        f,
        t("Save", "저장"),
        () -> {
          long result =
              store.saveProduct(
                  id,
                  sku.getText().toString(),
                  name.getText().toString(),
                  color.getText().toString(),
                  sizes.getText().toString(),
                  ratios.getText().toString(),
                  Money.parse(price.getText().toString()),
                  Rules.quantity(stock.getText().toString(), true));
          if (after != null) after.run(result);
        });
  }

  private void customerPage() throws Exception {
    addText(body, t("Customers", "고객 목록"), 25, INK, true);
    addButton(body, t("+ Add customer", "+ 고객 등록"), true, () -> customerForm(0, null));
    EditText search = input(body, t("Search name, company, phone or email", "이름·회사·전화·이메일 검색"), "");
    CheckBox archived = new CheckBox(this);
    archived.setText(t("Show archived customers", "보관한 고객 보기"));
    body.addView(archived);
    LinearLayout list = column();
    body.addView(list);
    Action refresh =
        () -> {
          list.removeAllViews();
          JSONArray a = store.customers(search.getText().toString(), archived.isChecked());
          if (a.length() == 0)
            addText(list, t("No matching customers.", "고객이 없습니다."), 14, MUTED, false);
          for (int i = 0; i < a.length(); i++) {
            JSONObject c = a.getJSONObject(i);
            long id = c.getLong("id");
            LinearLayout card = card(list);
            addText(card, c.getString("name"), 18, INK, true);
            addText(card, c.getString("company"), 14, MUTED, false);
            addText(card, c.getString("phone") + "  " + c.getString("email"), 13, MUTED, false);
            addButton(card, t("Edit customer", "고객 편집"), false, () -> customerForm(id, null));
            if (!archived.isChecked())
              addButton(
                  card,
                  t("Create order", "이 고객의 주문 만들기"),
                  false,
                  () -> {
                    orderId = store.newOrder(id);
                    go("order");
                  });
          }
        };
    watch(search, refresh);
    archived.setOnCheckedChangeListener((b, c) -> guard(refresh));
    refresh.run();
  }

  private void customerForm(long id, IdAction after) throws Exception {
    JSONObject c = id > 0 ? store.one("customers", id) : new JSONObject();
    LinearLayout f = column();
    EditText name = input(f, t("Contact name *", "고객 이름 *"), c.optString("name")),
        company = input(f, t("Company", "회사 / 매장"), c.optString("company"));
    EditText
        phone = field(f, t("Phone", "전화"), c.optString("phone"), InputType.TYPE_CLASS_PHONE, 1),
        email =
            field(
                f,
                t("Email", "이메일"),
                c.optString("email"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                1);
    EditText bill = multiline(f, t("Billing address", "청구 주소"), c.optString("billing")),
        ship = multiline(f, t("Shipping address", "배송 주소"), c.optString("shipping"));
    addButton(
        f,
        t("Copy billing address to shipping", "청구 주소를 배송 주소로 복사"),
        false,
        () -> ship.setText(bill.getText()));
    EditText notes = multiline(f, t("Notes", "메모"), c.optString("notes"));
    if (id > 0) {
      boolean archived = c.getInt("archived") == 1;
      addButton(
          f,
          archived ? t("Restore customer", "고객 복원") : t("Archive customer", "고객 보관"),
          false,
          () ->
              confirm(
                  t(
                      "Change archive status? Previous orders keep their customer snapshot.",
                      "보관 상태를 변경할까요? 이전 주문의 고객 정보는 유지됩니다."),
                  () -> {
                    store.archive("customers", id, !archived);
                    closeForm(f);
                    render();
                    toast("Archive status changed.", "보관 상태를 변경했습니다.");
                  }));
    }
    dialog(
        t("Customer", "고객"),
        f,
        t("Save", "저장"),
        () -> {
          long result =
              store.saveCustomer(
                  id,
                  name.getText().toString(),
                  company.getText().toString(),
                  phone.getText().toString(),
                  email.getText().toString(),
                  bill.getText().toString(),
                  ship.getText().toString(),
                  notes.getText().toString());
          if (after != null) after.run(result);
        });
  }

  private void orderPage() throws Exception {
    addText(body, t("Order history", "주문 이력"), 25, INK, true);
    addButton(
        body,
        t("+ Create order", "+ 새 주문"),
        true,
        () -> {
          orderId = store.newOrder(0);
          go("order");
        });
    EditText search = input(body, t("Search order number or customer", "주문 번호·고객 검색"), "");
    LinearLayout list = column();
    body.addView(list);
    Action refresh =
        () -> {
          list.removeAllViews();
          JSONArray a = store.orders();
          int shown = 0;
          String term = search.getText().toString().toLowerCase(Locale.ROOT);
          for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            String value =
                number(o.getLong("id"))
                    + " "
                    + o.getString("customer_snapshot")
                    + " "
                    + status(o.getString("status"));
            if (value.toLowerCase(Locale.ROOT).contains(term)) {
              orderRow(list, o);
              shown++;
            }
          }
          if (shown == 0) addText(list, t("No matching orders.", "주문이 없습니다."), 14, MUTED, false);
        };
    watch(search, refresh);
    refresh.run();
  }

  private void orderRow(LinearLayout p, JSONObject o) throws Exception {
    long id = o.getLong("id");
    JSONObject c = new JSONObject(o.getString("customer_snapshot"));
    LinearLayout row = card(p);
    addText(row, number(id) + "  ·  " + status(o.getString("status")), 16, TEAL, true);
    addText(row, c.optString("name", t("Customer not selected", "고객 미선택")), 17, INK, true);
    addText(
        row,
        Money.format(o.getLong("total")) + "  ·  " + date(o.getLong("updated")),
        12,
        MUTED,
        false);
    addButton(
        row,
        t("Open order", "주문 열기"),
        false,
        () -> {
          orderId = id;
          go("order");
        });
  }

  private void orderEditor() throws Exception {
    JSONObject o = store.one("orders", orderId);
    boolean draft = o.getString("status").equals("DRAFT");
    JSONObject customer = new JSONObject(o.getString("customer_snapshot"));
    addText(body, number(orderId) + " · " + status(o.getString("status")), 25, INK, true);
    addText(
        body,
        draft
            ? t(
                "Draft changes are saved automatically. Stock changes only when you complete the"
                    + " order.",
                "초안 변경은 자동 저장됩니다. 주문을 확정할 때 재고가 차감됩니다.")
            : date(o.getLong("updated")),
        13,
        MUTED,
        false);
    LinearLayout who = card(body);
    addText(who, t("CUSTOMER", "고객"), 11, MUTED, true);
    addText(who, customer.optString("name", t("Choose a customer", "고객을 선택해 주세요")), 19, INK, true);
    addText(who, customer.optString("company"), 14, MUTED, false);
    if (!customer.optString("shipping").isEmpty())
      addText(who, customer.optString("shipping"), 13, MUTED, false);
    if (draft)
      addButton(
          who, t("Choose / refresh customer", "고객 선택 / 정보 새로고침"), false, () -> pickCustomer());
    JSONArray items = store.items(orderId);
    for (int i = 0; i < items.length(); i++) {
      JSONObject l = items.getJSONObject(i);
      LinearLayout line = card(body);
      addText(line, l.getString("name") + " · " + l.getString("color"), 17, INK, true);
      addText(
          line,
          l.getString("sku")
              + "  ·  "
              + l.getInt("packs")
              + t(" packs × ", "팩 × ")
              + l.getInt("pack_size")
              + t(" units", "개"),
          13,
          MUTED,
          false);
      addText(line, Money.format(Store.lineTotal(l)), 21, INK, true);
      if (l.getLong("discount") > 0)
        addText(
            line,
            t("Unit discount: ", "개당 할인: ") + Money.format(l.getLong("discount")),
            12,
            MUTED,
            false);
      if (draft)
        addButton(
            line, t("Quantity / discount / remove", "수량 / 할인 / 삭제"), false, () -> lineForm(l));
    }
    if (draft)
      addButton(
          body, t("+ Add product / enter SKU", "+ 상품 추가 / SKU 입력"), true, () -> pickProduct());
    LinearLayout totals = card(body);
    addText(
        totals, t("Subtotal  ", "소계  ") + Money.format(store.subtotal(orderId)), 15, MUTED, false);
    addText(
        totals,
        t("Order discount  ", "주문 할인  ") + Money.format(o.getLong("discount")),
        15,
        MUTED,
        false);
    addText(totals, t("TOTAL  ", "총액  ") + Money.format(o.getLong("total")), 26, INK, true);
    addText(
        totals,
        t("Paid  ", "지불액  ")
            + Money.format(o.getLong("paid"))
            + t("  ·  Balance  ", "  ·  잔액  ")
            + Money.format(o.getLong("total") - o.getLong("paid")),
        14,
        INK,
        false);
    addText(totals, t("Payment record: ", "결제수단 기록: ") + o.getString("payment"), 12, MUTED, false);
    if (!o.getString("notes").isEmpty()) addText(totals, o.getString("notes"), 14, MUTED, false);
    if (draft)
      addButton(totals, t("Discount, payment & notes", "할인·지불액·메모 편집"), false, () -> detailsForm());
    LinearLayout sig = card(body);
    boolean hasSignature = !o.getString("signature").isEmpty();
    addText(
        sig,
        hasSignature ? t("Signature saved", "서명 저장됨") : t("Signature (optional)", "서명 (선택 사항)"),
        15,
        INK,
        true);
    if (hasSignature) {
      byte[] b = android.util.Base64.decode(o.getString("signature"), android.util.Base64.DEFAULT);
      android.graphics.Bitmap bitmap =
          android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length);
      if (bitmap != null) {
        android.widget.ImageView image = new android.widget.ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(dp(110));
        image.setContentDescription(t("Saved signature", "저장된 서명"));
        sig.addView(image, new LinearLayout.LayoutParams(-1, dp(110)));
      }
    }
    if (draft) {
      addButton(sig, t("Draw signature", "서명 작성"), false, () -> signatureDialog());
      if (hasSignature)
        addButton(
            sig,
            t("Clear signature", "서명 지우기"),
            false,
            () ->
                confirm(
                    t("Remove this signature?", "서명을 삭제할까요?"),
                    () -> {
                      store.signature(orderId, "");
                      render();
                    }));
      addButton(
          body,
          t("Complete order", "주문 확정"),
          true,
          () ->
              confirm(
                  t(
                      "Complete this order and deduct its item quantities from stock?",
                      "주문을 확정하고 해당 수량을 재고에서 차감할까요?"),
                  () -> {
                    store.complete(orderId);
                    render();
                    toast("Order completed.", "주문을 확정했습니다.");
                  }));
    }
    addButton(
        body,
        t("Duplicate as new draft", "새 초안으로 복제"),
        false,
        () ->
            confirm(
                t(
                    "Create a new draft using current catalog prices? Discounts, payments and"
                        + " signatures will start empty.",
                    "현재 상품 가격으로 새 초안을 만들까요? 할인·지불액·서명은 새로 입력합니다."),
                () -> {
                  orderId = store.duplicate(orderId);
                  render();
                }));
    if (!o.getString("status").equals("CANCELLED"))
      addButton(
          body,
          t("Cancel order", "주문 취소"),
          false,
          () ->
              confirm(
                  draft
                      ? t("Cancel this draft? It will remain in history.", "초안을 취소할까요? 이력에는 남습니다.")
                      : t(
                          "Cancel this completed order and restore its stock?",
                          "완료 주문을 취소하고 재고를 복원할까요?"),
                  () -> {
                    store.cancel(orderId);
                    render();
                  }));
  }

  private void pickCustomer() throws Exception {
    LinearLayout f = column();
    f.setPadding(dp(16), dp(12), dp(16), dp(8));
    EditText search = input(f, t("Search customer", "고객 검색"), "");
    ScrollView s = new ScrollView(this);
    LinearLayout list = column();
    s.addView(list);
    f.addView(s, new LinearLayout.LayoutParams(-1, dp(300)));
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(t("Select customer", "고객 선택"))
            .setView(f)
            .setNegativeButton(t("Close", "닫기"), null)
            .create();
    Action refresh =
        () -> {
          list.removeAllViews();
          JSONArray a = store.customers(search.getText().toString(), false);
          for (int i = 0; i < a.length(); i++) {
            JSONObject c = a.getJSONObject(i);
            addButton(
                list,
                c.getString("name") + " · " + c.getString("company"),
                false,
                () -> {
                  store.setCustomer(orderId, c.getLong("id"));
                  d.dismiss();
                  render();
                });
          }
          if (a.length() == 0) addText(list, t("No matches", "검색 결과 없음"), 14, MUTED, false);
        };
    watch(search, refresh);
    refresh.run();
    addButton(
        f,
        t("+ New customer", "+ 새 고객"),
        false,
        () -> {
          d.dismiss();
          customerForm(0, id -> store.setCustomer(orderId, id));
        });
    d.show();
  }

  private void pickProduct() throws Exception {
    LinearLayout f = column();
    f.setPadding(dp(16), dp(12), dp(16), dp(8));
    EditText search = input(f, t("SKU, barcode or product name", "SKU·바코드 번호·상품명"), "");
    search.setImeOptions(EditorInfo.IME_ACTION_DONE);
    addText(
        f,
        t(
            "Type a SKU, or use a keyboard-mode USB / Bluetooth scanner and press Enter.",
            "SKU를 입력하거나 키보드 방식 USB·Bluetooth 스캐너로 입력한 뒤 Enter를 누르세요."),
        12,
        MUTED,
        false);
    ScrollView s = new ScrollView(this);
    LinearLayout list = column();
    s.addView(list);
    f.addView(s, new LinearLayout.LayoutParams(-1, dp(270)));
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(t("Add product", "상품 추가"))
            .setView(f)
            .setNegativeButton(t("Close", "닫기"), null)
            .create();
    Action refresh =
        () -> {
          list.removeAllViews();
          JSONArray a = store.products(search.getText().toString(), false);
          for (int i = 0; i < a.length(); i++) {
            JSONObject p = a.getJSONObject(i);
            addButton(
                list,
                p.getString("sku")
                    + " · "
                    + p.getString("name")
                    + "\n"
                    + p.getString("color")
                    + " · "
                    + Money.format(p.getLong("price"))
                    + " · "
                    + p.getInt("stock")
                    + t(" in stock", "개 재고"),
                false,
                () -> {
                  store.addProduct(orderId, p.getLong("id"));
                  d.dismiss();
                  render();
                });
          }
          if (a.length() == 0) addText(list, t("No matches", "검색 결과 없음"), 14, MUTED, false);
        };
    watch(search, refresh);
    search.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_DONE
              || (event != null
                  && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                  && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
            guard(
                () -> {
                  JSONArray a =
                      store.query(
                          "SELECT id FROM products WHERE sku=? COLLATE NOCASE AND archived=0",
                          search.getText().toString().trim());
                  if (a.length() == 1) {
                    store.addProduct(orderId, a.getJSONObject(0).getLong("id"));
                    d.dismiss();
                    render();
                  } else toast("No exact SKU match.", "일치하는 SKU가 없습니다.");
                });
            return true;
          }
          return false;
        });
    refresh.run();
    addButton(
        f,
        t("+ New product", "+ 새 상품"),
        false,
        () -> {
          d.dismiss();
          productForm(0, id -> store.addProduct(orderId, id));
        });
    d.show();
  }

  private void lineForm(JSONObject line) throws Exception {
    long id = line.getLong("id");
    LinearLayout f = column();
    addText(f, line.getString("sku") + " · " + line.getString("name"), 16, INK, true);
    EditText packs = qty(f, t("Number of packs", "팩 수량"), line.getInt("packs")),
        discount =
            money(
                f,
                t("Discount per individual unit (USD)", "개당 할인액 (USD)"),
                line.getLong("discount"));
    addText(
        f,
        t("Recorded unit price: ", "기록된 개당 가격: ") + Money.format(line.getLong("price")),
        13,
        MUTED,
        false);
    addButton(
        f,
        t("Remove item", "품목 삭제"),
        false,
        () ->
            confirm(
                t("Remove this line?", "이 품목을 삭제할까요?"),
                () -> {
                  store.removeLine(id);
                  closeForm(f);
                  render();
                  toast("Item removed.", "품목을 삭제했습니다.");
                }));
    dialog(
        t("Order item", "주문 품목"),
        f,
        t("Save", "저장"),
        () ->
            store.updateLine(
                id,
                Rules.quantity(packs.getText().toString(), false),
                Money.parse(discount.getText().toString())));
  }

  private void detailsForm() throws Exception {
    JSONObject o = store.one("orders", orderId);
    LinearLayout f = column();
    EditText
        discount = money(f, t("Order discount (USD)", "주문 전체 할인액 (USD)"), o.getLong("discount")),
        paid = money(f, t("Amount paid (USD)", "지불액 (USD)"), o.getLong("paid")),
        method = input(f, t("Payment method (record only)", "결제수단 (기록용)"), o.getString("payment")),
        notes =
            multiline(
                f, t("Order notes / shipping instructions", "주문 메모 / 배송 요청"), o.getString("notes"));
    addText(
        f,
        t(
            "This app records payments. Card charging and bank transfers are handled separately.",
            "지불 내역을 기록하는 기능입니다. 카드 승인이나 은행 이체는 별도로 처리합니다."),
        12,
        MUTED,
        false);
    dialog(
        t("Order details", "주문 상세"),
        f,
        t("Save", "저장"),
        () ->
            store.saveDetails(
                orderId,
                Money.parse(discount.getText().toString()),
                Money.parse(paid.getText().toString()),
                method.getText().toString(),
                notes.getText().toString()));
  }

  private void signatureDialog() {
    LinearLayout f = column();
    SignatureView sign = new SignatureView(this);
    f.addView(sign, new LinearLayout.LayoutParams(-1, dp(210)));
    addButton(f, t("Clear", "지우기"), false, () -> sign.clear());
    dialog(
        t("Draw signature", "서명 작성"),
        f,
        t("Save signature", "서명 저장"),
        () -> store.signature(orderId, sign.encoded()));
  }

  private void settingsPage() throws Exception {
    addText(body, t("Make it yours", "설정과 백업"), 25, INK, true);
    JSONObject settings = store.one("settings", 1);
    LinearLayout c = card(body);
    addText(c, settings.getString("organization"), 20, INK, true);
    addText(
        c,
        t("Organization name and policy stored with your local settings.", "로컬 설정에 저장할 이름과 정책을 설정합니다."),
        14,
        MUTED,
        false);
    addButton(
        c,
        t("Edit organization / policy", "이름 / 정책 편집"),
        false,
        () -> {
          LinearLayout f = column();
          EditText
              name =
                  input(
                      f,
                      t("Organization or project name", "조직 / 프로젝트 이름"),
                      settings.getString("organization")),
              policy =
                  multiline(f, t("Terms / policy", "거래 조건 / 정책"), settings.getString("policy"));
          dialog(
              t("Organization settings", "조직 설정"),
              f,
              t("Save", "저장"),
              () -> store.settings(name.getText().toString(), policy.getText().toString()));
        });
    c = card(body);
    addText(c, t("Backup & restore", "백업과 복원"), 19, INK, true);
    addText(
        c,
        t(
            "Data stays in this app. Export a backup before uninstalling or changing devices."
                + " Backups include customers, orders and signatures.",
            "자료는 앱 안에 저장됩니다. 앱 삭제나 기기 변경 전에 백업해 주세요. 백업에는 고객·주문·서명이 포함됩니다."),
        14,
        MUTED,
        false);
    addButton(
        c,
        t("Export complete backup", "전체 자료 백업"),
        true,
        () ->
            createDocument(
                "application/json",
                "ordiloom-backup-"
                    + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date())
                    + ".json",
                EXPORT));
    addButton(
        c,
        t("Restore from backup", "백업 파일에서 복원"),
        false,
        () -> {
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.setType("*/*");
          startActivityForResult(i, IMPORT);
        });
    c = card(body);
    addText(c, t("Language", "언어"), 19, INK, true);
    addButton(
        c,
        "English",
        false,
        () -> {
          prefs.edit().putString("language", "en").apply();
          render();
        });
    addButton(
        c,
        "한국어",
        false,
        () -> {
          prefs.edit().putString("language", "ko").apply();
          render();
        });
    addButton(
        c,
        t("Use device language", "기기 언어 사용"),
        false,
        () -> {
          prefs.edit().putString("language", "system").apply();
          render();
        });
    c = card(body);
    addText(c, "ORDILOOM Community 0.1.0", 16, INK, true);
    addButton(c, t("Privacy policy", "개인정보처리방침"), false, () -> {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://ordiloom-privacy-hsshin.hsshin-iau.chatgpt.site")));
      } catch (android.content.ActivityNotFoundException e) {
        new AlertDialog.Builder(this)
            .setTitle(t("Privacy policy", "개인정보처리방침"))
            .setMessage("https://ordiloom-privacy-hsshin.hsshin-iau.chatgpt.site")
            .setPositiveButton(android.R.string.ok, null).show();
      }
    });
    addText(
        c,
        t(
            "Offline order and inventory study project. No account or server is required. Android"
                + " 5.0+. Prices are in USD; taxes and payment processing are not calculated.",
            "계정이나 서버 없이 사용하는 주문·재고 학습 프로젝트입니다. Android 5.0 이상을 대상으로 합니다. 가격은 USD이며 세금 계산과 결제 승인은"
                + " 포함하지 않습니다."),
        13,
        MUTED,
        false);
  }

  private void createDocument(String type, String name, int code) {
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType(type);
    i.putExtra(Intent.EXTRA_TITLE, name);
    startActivityForResult(i, code);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == EXPORT)
      async(
          s -> {
            byte[] bytes = s.backup().toString(2).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 10000000) throw new IllegalArgumentException("backup");
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
              if (out == null) throw new IllegalArgumentException("file");
              out.write(bytes);
            }
            return null;
          },
          value -> toast("Backup saved.", "백업을 저장했습니다."));
    else if (request == IMPORT)
      async(
          s -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              if (in == null) throw new IllegalArgumentException("backup");
              byte[] buf = new byte[8192];
              int n;
              while ((n = in.read(buf)) != -1) {
                if (bytes.size() + n > 10000000) throw new IllegalArgumentException("backup");
                bytes.write(buf, 0, n);
              }
            }
            JSONObject backup =
                new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            Store.validateBackup(backup);
            return backup;
          },
          value -> {
            JSONObject backup = (JSONObject) value;
            confirm(
                t(
                        "Replace all local data with this backup? Export your current data first if"
                            + " needed.\n\n",
                        "이 백업으로 현재 자료 전체를 교체할까요? 필요한 경우 먼저 현재 자료를 백업해 주세요.\n\n")
                    + backup.getJSONArray("products").length()
                    + t(" products · ", "개 상품 · ")
                    + backup.getJSONArray("customers").length()
                    + t(" customers · ", "명 고객 · ")
                    + backup.getJSONArray("orders").length()
                    + t(" orders", "건 주문"),
                () ->
                    async(
                        s -> {
                          s.restore(backup);
                          return null;
                        },
                        v -> {
                          orderId = 0;
                          screen = "home";
                          render();
                          toast("Backup restored.", "백업을 복원했습니다.");
                        }));
          });
  }

  private void async(Job job, Result done) {
    if (busy) return;
    busy = true;
    final int orientation = getRequestedOrientation();
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    AlertDialog progress =
        new AlertDialog.Builder(this)
            .setMessage(t("Working…", "처리 중입니다…"))
            .setCancelable(false)
            .create();
    progress.show();
    new Thread(
            () -> {
              Object value = null;
              Exception failure = null;
              Store db = new Store(getApplicationContext());
              try {
                value = job.run(db);
              } catch (Exception e) {
                failure = e;
              } finally {
                db.close();
              }
              final Object result = value;
              final Exception error = failure;
              runOnUiThread(
                  () -> {
                    busy = false;
                    if (isFinishing() || isDestroyed()) return;
                    progress.dismiss();
                    setRequestedOrientation(orientation);
                    if (error != null) error(error);
                    else guard(() -> done.run(result));
                  });
            },
            "local-file-operation")
        .start();
  }
}
