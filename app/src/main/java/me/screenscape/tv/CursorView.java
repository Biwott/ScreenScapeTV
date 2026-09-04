package me.screenscape.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;

/**
 * An on-screen pointer driven by the TV remote's D-pad.
 *
 * Holding a direction accelerates the pointer, so crossing a 1080p screen is quick
 * but small adjustments stay precise. Pushing against a screen edge scrolls the page
 * underneath instead of stalling.
 */
public class CursorView extends View {

    private static final float SPEED_MIN = 6f;      // px per frame at key-down
    private static final float SPEED_MAX = 46f;     // px per frame at full acceleration
    private static final float ACCEL = 1.09f;       // per-frame multiplier while held
    private static final long FRAME_MS = 16;        // ~60fps
    private static final float EDGE_MARGIN = 90f;   // distance from edge that triggers scroll
    private static final int SCROLL_STEP = 34;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView web;
    private float x = -1, y = -1;
    private float speed = SPEED_MIN;
    private int dx = 0, dy = 0;
    private boolean moving = false;

    private final Runnable stepper = new Runnable() {
        @Override
        public void run() {
            if (!moving) return;
            step();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    public CursorView(Context c) { super(c); init(); }
    public CursorView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        fill.setColor(Color.WHITE);
        stroke.setColor(0xFF1A1A1A);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3.5f);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        shadow.setColor(0x66000000);
        setWillNotDraw(false);

        // Classic pointer silhouette, drawn at the origin and translated when painted.
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 40);
        arrow.lineTo(10.5f, 30.5f);
        arrow.lineTo(17.5f, 45.5f);
        arrow.lineTo(24.5f, 42f);
        arrow.lineTo(17.5f, 27.5f);
        arrow.lineTo(30f, 26.5f);
        arrow.close();
    }

    void attachTo(WebView target) {
        this.web = target;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (x < 0 || y < 0) {   // start in the middle of the screen
            x = w / 2f;
            y = h / 2f;
        }
    }

    /** @return true if the event was consumed by the pointer. */
    boolean handleKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = e.getAction() == KeyEvent.ACTION_UP;

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                setDirection(down ? -1 : 0, dyIfHeld(code), down, up, code);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                setDirection(down ? 1 : 0, dyIfHeld(code), down, up, code);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                setDirection(dxIfHeld(code), down ? -1 : 0, down, up, code);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                setDirection(dxIfHeld(code), down ? 1 : 0, down, up, code);
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (up && web != null) {
                    MainActivity.tap(web, x, y);
                }
                return true;
        }
        return false;
    }

    // Diagonal movement: keep the other axis if that key is still held.
    private int dxIfHeld(int ignored) { return dx; }
    private int dyIfHeld(int ignored) { return dy; }

    private void setDirection(int newDx, int newDy, boolean down, boolean up, int code) {
        if (down) {
            dx = newDx;
            dy = newDy;
            if (!moving) {
                moving = true;
                speed = SPEED_MIN;
                handler.post(stepper);
            }
        } else if (up) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 0;
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) dy = 0;
            if (dx == 0 && dy == 0) {
                moving = false;
                speed = SPEED_MIN;
                handler.removeCallbacks(stepper);
            }
        }
    }

    private void step() {
        if (dx == 0 && dy == 0) return;

        speed = Math.min(SPEED_MAX, speed * ACCEL);

        // Normalise so diagonals aren't faster than straight lines.
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        x += (dx / len) * speed;
        y += (dy / len) * speed;

        x = Math.max(0, Math.min(getWidth() - 1, x));
        y = Math.max(0, Math.min(getHeight() - 1, y));

        // Scroll the page when the pointer is pressed against an edge.
        if (web != null) {
            if (dy < 0 && y <= EDGE_MARGIN) web.scrollBy(0, -SCROLL_STEP);
            else if (dy > 0 && y >= getHeight() - EDGE_MARGIN) web.scrollBy(0, SCROLL_STEP);
            if (dx < 0 && x <= EDGE_MARGIN) web.scrollBy(-SCROLL_STEP, 0);
            else if (dx > 0 && x >= getWidth() - EDGE_MARGIN) web.scrollBy(SCROLL_STEP, 0);
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(x + 2.5f, y + 3f);
        canvas.drawPath(arrow, shadow);     // soft offset shadow for contrast on light pages
        canvas.restore();

        canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, stroke);
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(stepper);
        super.onDetachedFromWindow();
    }
}
