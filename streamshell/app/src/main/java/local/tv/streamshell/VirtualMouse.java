package local.tv.streamshell;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

/**
 * An on-screen pointer moved with the remote's D-pad. The page receives real mouse events
 * (hover, press, release, wheel), which is what a site built for desktop browsers expects.
 *
 * Event recipe for WebView 137+: hover and wheel go through the generic-motion path; a click is
 * ACTION_DOWN/UP on the touch path plus ACTION_BUTTON_PRESS/RELEASE on the generic path, with the
 * button carried in buttonState.
 */
final class VirtualMouse extends View {
    private static final long HIDE_AFTER_MS = 4000;
    private static final float BASE_STEP = 22f;
    private static final float MAX_STEP = 110f;
    private static final float EDGE = 48f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final Runnable hide = () -> { visible = false; invalidate(); };

    private View target;
    private float x = -1, y = -1;
    private boolean visible;
    private boolean pressed;
    private long downTime;

    VirtualMouse(Context context) {
        super(context);
        setFocusable(false);
        setClickable(false);
        fill.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);
        outline.setColor(Color.BLACK);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(3f);
        outline.setStrokeJoin(Paint.Join.ROUND);
        // Classic arrow, tip at (0,0), about 44 px tall on a 1080p screen.
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 40);
        arrow.lineTo(10, 31);
        arrow.lineTo(17, 46);
        arrow.lineTo(24, 43);
        arrow.lineTo(17, 28);
        arrow.lineTo(30, 28);
        arrow.close();
    }

    /** The view that receives the mouse events: the WebView, or its fullscreen video view. */
    void setTarget(View target) {
        this.target = target;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (x < 0) {
            x = w / 2f;
            y = h / 2f;
        }
        x = Math.min(x, Math.max(0, w - 1));
        y = Math.min(y, Math.max(0, h - 1));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!visible) return;
        canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, outline);
        canvas.restore();
    }

    /**
     * Moves the pointer one step; holding the key (repeat > 0) accelerates. Pushing against the
     * top or bottom edge scrolls the page instead.
     */
    void nudge(int dx, int dy, int repeat) {
        float step = Math.min(BASE_STEP + repeat * 7f, MAX_STEP);
        float nx = x + dx * step;
        float ny = y + dy * step;
        float maxX = getWidth() - 1, maxY = getHeight() - 1;
        if (!pressed && ((dy < 0 && y <= EDGE) || (dy > 0 && y >= maxY - EDGE))) {
            // Wheel units are "clicks"; a negative value scrolls the page down.
            send(MotionEvent.ACTION_SCROLL, 0, -dy * (repeat > 3 ? 3f : 1.5f));
        }
        x = Math.max(0, Math.min(nx, maxX));
        y = Math.max(0, Math.min(ny, maxY));
        send(pressed ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_HOVER_MOVE,
                pressed ? MotionEvent.BUTTON_PRIMARY : 0, 0);
        show();
    }

    void press() {
        if (pressed) return;
        pressed = true;
        downTime = SystemClock.uptimeMillis();
        send(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, 0);
        send(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_PRIMARY, 0);
        show();
    }

    void release() {
        if (!pressed) return;
        pressed = false;
        send(MotionEvent.ACTION_BUTTON_RELEASE, MotionEvent.BUTTON_PRIMARY, 0);
        send(MotionEvent.ACTION_UP, 0, 0);
        show();
    }

    private void show() {
        visible = true;
        invalidate();
        removeCallbacks(hide);
        postDelayed(hide, HIDE_AFTER_MS);
    }

    private void send(int action, int buttonState, float wheel) {
        if (target == null) return;
        MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
        props.id = 0;
        props.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.x = x;
        coords.y = y;
        coords.pressure = buttonState != 0 ? 1f : 0f;
        coords.size = 1f;
        if (wheel != 0) coords.setAxisValue(MotionEvent.AXIS_VSCROLL, wheel);

        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime == 0 ? now : downTime, now, action, 1,
                new MotionEvent.PointerProperties[]{props}, new MotionEvent.PointerCoords[]{coords},
                0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        boolean touchPath = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_MOVE;
        if (touchPath) {
            target.dispatchTouchEvent(event);
        } else {
            target.dispatchGenericMotionEvent(event);
        }
        event.recycle();
    }
}
