package com.sketchplay.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class SketchGameView extends View {
    public interface OnWinListener { void onWin(); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap photo;
    private final List<RectF> platforms = new ArrayList<>();
    private final List<PointF> coins = new ArrayList<>();
    private final List<Boolean> collected = new ArrayList<>();
    private RectF goal;
    private PointF playerStart;
    private float playerX, playerY, velocityY, radius;
    private float worldW, worldH;
    private boolean playing, left, right, jumpLatch, onGround;
    private long lastFrame;
    private OnWinListener winListener;

    public SketchGameView(Context context) { super(context); paint.setStrokeCap(Paint.Cap.ROUND); setFocusable(true); }
    public void setOnWinListener(OnWinListener listener) { winListener = listener; }
    public boolean hasPhoto() { return photo != null; }

    public void setPhoto(Bitmap bitmap) {
        photo = bitmap;
        worldW = bitmap.getWidth(); worldH = bitmap.getHeight();
        analyze(); playing = false; invalidate();
    }

    private void analyze() {
        platforms.clear(); coins.clear(); collected.clear(); goal = null; playerStart = null;
        List<RectF> black = components(0), yellow = components(1), blue = components(2), green = components(3);
        platforms.addAll(black);
        for (RectF r : yellow) { coins.add(new PointF(r.centerX(), r.centerY())); collected.add(false); }
        if (!blue.isEmpty()) goal = largest(blue);
        if (!green.isEmpty()) { RectF p = largest(green); playerStart = new PointF(p.centerX(), p.centerY()); }
        if (playerStart == null) playerStart = new PointF(worldW * .14f, worldH * .78f);
        if (goal == null) goal = new RectF(worldW * .82f, worldH * .10f, worldW * .92f, worldH * .22f);
        if (platforms.isEmpty()) platforms.add(new RectF(0, worldH * .88f, worldW, worldH * .96f));
    }

    private RectF largest(List<RectF> list) {
        RectF best = list.get(0);
        for (RectF r : list) if (r.width() * r.height() > best.width() * best.height()) best = r;
        return best;
    }

    private List<RectF> components(int type) {
        int w = photo.getWidth(), h = photo.getHeight();
        int step = Math.max(1, Math.max(w, h) / 650);
        int gw = (w + step - 1) / step, gh = (h + step - 1) / step;
        boolean[] mask = new boolean[gw * gh];
        for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++) mask[y * gw + x] = matches(photo.getPixel(Math.min(w - 1, x * step), Math.min(h - 1, y * step)), type);
        boolean[] seen = new boolean[mask.length];
        List<RectF> result = new ArrayList<>();
        for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++) {
            int start = y * gw + x;
            if (!mask[start] || seen[start]) continue;
            ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(start); seen[start] = true;
            int minX = x, maxX = x, minY = y, maxY = y, count = 0;
            while (!queue.isEmpty()) {
                int p = queue.removeFirst(), px = p % gw, py = p / gw; count++;
                minX = Math.min(minX, px); maxX = Math.max(maxX, px); minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                if (px > 0) visit(queue, seen, mask, p - 1);
                if (px + 1 < gw) visit(queue, seen, mask, p + 1);
                if (py > 0) visit(queue, seen, mask, p - gw);
                if (py + 1 < gh) visit(queue, seen, mask, p + gw);
            }
            int minCount = type == 0 ? 7 : 3;
            if (count >= minCount) result.add(new RectF(minX * step, minY * step, (maxX + 1) * step, (maxY + 1) * step));
        }
        return result;
    }

    private void visit(ArrayDeque<Integer> q, boolean[] seen, boolean[] mask, int p) { if (mask[p] && !seen[p]) { seen[p] = true; q.add(p); } }

    private boolean matches(int color, int type) {
        float[] hsv = new float[3]; Color.colorToHSV(color, hsv);
        float hue = hsv[0], sat = hsv[1], value = hsv[2];
        if (type == 0) return value < .30f && sat < .55f;
        if (sat < .38f || value < .28f) return false;
        if (type == 1) return hue >= 32 && hue <= 75;
        if (type == 2) return hue >= 185 && hue <= 275;
        return hue >= 75 && hue <= 175;
    }

    public void startGame() {
        if (photo == null) return;
        playerX = playerStart.x; playerY = playerStart.y; velocityY = 0; radius = Math.max(12, Math.min(worldW, worldH) * .018f);
        for (int i = 0; i < collected.size(); i++) collected.set(i, false);
        playing = true; lastFrame = System.nanoTime(); invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (photo == null) { canvas.drawColor(0xfff7f7f2); paint.setColor(0xff17202a); paint.setTextSize(24); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("صوّر رسمة لتبدأ", getWidth() / 2f, getHeight() / 2f, paint); return; }
        canvas.drawBitmap(photo, null, new RectF(0, 0, getWidth(), getHeight()), paint);
        float sx = getWidth() / worldW, sy = getHeight() / worldH;
        if (playing) {
            update();
            for (int i = 0; i < coins.size(); i++) if (!collected.get(i)) { PointF c = coins.get(i); paint.setColor(0xffffc400); canvas.drawCircle(c.x * sx, c.y * sy, Math.max(7, radius * sx * .7f), paint); }
            paint.setColor(0xff1976d2); canvas.drawRect(goal.left * sx, goal.top * sy, goal.right * sx, goal.bottom * sy, paint);
            paint.setColor(0xff27c45a); canvas.drawCircle(playerX * sx, playerY * sy, radius * sx, paint);
            drawControls(canvas);
            postInvalidateDelayed(16);
        } else {
            paint.setColor(0xaa17202a); paint.setTextSize(17); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("ألوانك أصبحت مرحلة — اضغط ابدأ اللعب", getWidth() / 2f, getHeight() - 28, paint);
        }
    }

    private void update() {
        float dt = Math.min(.04f, (System.nanoTime() - lastFrame) / 1_000_000_000f); lastFrame = System.nanoTime();
        float speed = worldW * .38f;
        if (left) playerX -= speed * dt; if (right) playerX += speed * dt;
        if (jumpLatch && onGround) { velocityY = -worldH * .72f; onGround = false; } jumpLatch = false;
        velocityY += worldH * 1.65f * dt; float nextY = playerY + velocityY * dt; boolean landed = false;
        if (velocityY >= 0) for (RectF p : platforms) if (playerX + radius > p.left && playerX - radius < p.right && playerY + radius <= p.top + 5 && nextY + radius >= p.top) { nextY = p.top - radius; velocityY = 0; landed = true; }
        playerY = nextY; onGround = landed;
        playerX = Math.max(radius, Math.min(worldW - radius, playerX));
        if (playerY > worldH + radius * 3) { playerX = playerStart.x; playerY = playerStart.y; velocityY = 0; }
        for (int i = 0; i < coins.size(); i++) { PointF c = coins.get(i); if (!collected.get(i) && dist(playerX, playerY, c.x, c.y) < radius * 2.2f) collected.set(i, true); }
        if (dist(playerX, playerY, goal.centerX(), goal.centerY()) < radius * 2.8f) { playing = false; if (winListener != null) winListener.onWin(); }
    }

    private float dist(float ax, float ay, float bx, float by) { float dx = ax - bx, dy = ay - by; return (float)Math.sqrt(dx * dx + dy * dy); }

    private void drawControls(Canvas c) {
        paint.setColor(0x99202020); c.drawCircle(getWidth() * .14f, getHeight() * .88f, 34, paint); c.drawCircle(getWidth() * .32f, getHeight() * .88f, 34, paint); c.drawCircle(getWidth() * .86f, getHeight() * .88f, 42, paint);
        paint.setColor(Color.WHITE); paint.setTextSize(25); paint.setTextAlign(Paint.Align.CENTER); c.drawText("‹", getWidth() * .14f, getHeight() * .895f, paint); c.drawText("›", getWidth() * .32f, getHeight() * .895f, paint); c.drawText("↑", getWidth() * .86f, getHeight() * .895f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!playing) return true;
        float x = event.getX(), y = event.getY(); boolean controls = y > getHeight() * .72f;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            left = controls && x < getWidth() * .23f; right = controls && x >= getWidth() * .23f && x < getWidth() * .46f;
            if (controls && x >= getWidth() * .65f && event.getAction() == MotionEvent.ACTION_DOWN) jumpLatch = true;
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) { left = right = false; }
        return true;
    }
}
