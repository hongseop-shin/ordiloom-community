package com.hongseop.ordiloom.community;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import java.io.ByteArrayOutputStream;

/** The signature stays in the private database; no shared-storage image is created. */
public final class SignatureView extends View {
  private final Path path = new Path();
  private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean marked = false;

  public SignatureView(Context c) {
    super(c);
    ink.setColor(Color.rgb(16, 44, 54));
    ink.setStyle(Paint.Style.STROKE);
    ink.setStrokeWidth(3 * c.getResources().getDisplayMetrics().density);
    ink.setStrokeCap(Paint.Cap.ROUND);
    ink.setStrokeJoin(Paint.Join.ROUND);
    setBackgroundColor(Color.WHITE);
    setContentDescription("Signature drawing area");
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawPath(path, ink);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    float x = Math.max(0, Math.min(getWidth(), e.getX())),
        y = Math.max(0, Math.min(getHeight(), e.getY()));
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      getParent().requestDisallowInterceptTouchEvent(true);
      path.moveTo(x, y);
      path.lineTo(x + .1f, y + .1f);
      marked = true;
    } else if (e.getAction() == MotionEvent.ACTION_MOVE) path.lineTo(x, y);
    else if (e.getAction() == MotionEvent.ACTION_UP) {
      path.lineTo(x, y);
      getParent().requestDisallowInterceptTouchEvent(false);
      performClick();
    }
    invalidate();
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  public void clear() {
    path.reset();
    marked = false;
    invalidate();
  }

  public String encoded() throws Exception {
    if (!marked || getWidth() < 1 || getHeight() < 1)
      throw new IllegalArgumentException("signature");
    float scale = Math.min(1f, 1200f / Math.max(getWidth(), getHeight()));
    Bitmap b =
        Bitmap.createBitmap(
            Math.max(1, (int) (getWidth() * scale)),
            Math.max(1, (int) (getHeight() * scale)),
            Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(b);
    c.drawColor(Color.WHITE);
    c.scale(scale, scale);
    c.drawPath(path, ink);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    b.compress(Bitmap.CompressFormat.PNG, 100, out);
    b.recycle();
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
  }
}
