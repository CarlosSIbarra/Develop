package com.sketchplay.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 21;
    private static final int REQ_PICK = 22;
    private static final int REQ_PERMISSIONS = 23;

    private SketchGameView gameView;
    private LinearLayout toolbar;
    private TextView hint;
    private Uri pendingPhotoUri;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff17202a);
        buildUi();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xfff7f7f2);
        gameView = new SketchGameView(this);
        gameView.setOnWinListener(() -> Toast.makeText(this, "أحسنت! وصلت إلى الهدف 🎉", Toast.LENGTH_LONG).show());
        root.addView(gameView, new FrameLayout.LayoutParams(-1, -1));

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        toolbar.setPadding(12, 8, 12, 8);
        toolbar.setBackgroundColor(0xee17202a);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(toolbar, barParams);

        hint = new TextView(this);
        hint.setText("ارسم بالألوان ثم صوّر الورقة: الأسود منصة • الأصفر عملة • الأزرق نهاية • الأخضر لاعب");
        hint.setTextColor(0xffffffff);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        toolbar.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        toolbar.addView(buttons, new LinearLayout.LayoutParams(-1, 54));
        Button camera = button("📷 تصوير");
        Button pick = button("🖼 اختيار صورة");
        Button play = button("▶ ابدأ اللعب");
        buttons.addView(camera, weightParams());
        buttons.addView(pick, weightParams());
        buttons.addView(play, weightParams());
        camera.setOnClickListener(v -> openCamera());
        pick.setOnClickListener(v -> openPicker());
        play.setOnClickListener(v -> {
            if (gameView.hasPhoto()) {
                toolbar.setVisibility(View.GONE);
                gameView.startGame();
            } else {
                Toast.makeText(this, "صوّر رسمة أو اختر صورة أولًا", Toast.LENGTH_SHORT).show();
            }
        });
        setContentView(root);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, -1, 1f);
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_PICK);
    }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= 23 && (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERMISSIONS);
            return;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "sketch_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        pendingPhotoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (pendingPhotoUri == null) {
            Toast.makeText(this, "تعذر إنشاء ملف الصورة", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, pendingPhotoUri);
        startActivityForResult(i, REQ_CAMERA);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERMISSIONS && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) openCamera();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        Uri uri = requestCode == REQ_CAMERA ? pendingPhotoUri : (data == null ? null : data.getData());
        if (uri != null) loadPhoto(uri);
    }

    private void loadPhoto(Uri uri) {
        hint.setText("جارٍ تحليل الرسمة…");
        new Thread(() -> {
            Bitmap bitmap = decodeScaled(uri);
            runOnUiThread(() -> {
                if (bitmap == null) {
                    hint.setText("تعذر قراءة الصورة");
                    return;
                }
                gameView.setPhoto(bitmap);
                toolbar.setVisibility(View.VISIBLE);
                hint.setText("تم التحليل. اضغط «ابدأ اللعب» — حرّك الشخصية باللمس");
            });
        }).start();
    }

    private Bitmap decodeScaled(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream a = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(a, null, bounds);
            if (a != null) a.close();
            int sample = 1;
            while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1200) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            InputStream b = getContentResolver().openInputStream(uri);
            Bitmap result = BitmapFactory.decodeStream(b, null, options);
            if (b != null) b.close();
            return result;
        } catch (Exception ignored) { return null; }
    }
}
