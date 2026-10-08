package com.rafiki.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements RecognitionListener {
    private static final int REQ_AUDIO = 40;
    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private Button micButton;
    private TextView status;
    private SpeechRecognizer recognizer;
    private TextToSpeech speaker;
    private AssistantEngine engine;
    private boolean listening;
    private boolean speakerReady;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 24, 39));
        engine = new AssistantEngine(this);
        buildUi();
        initSpeaker();
        initRecognizer();
        appendMessage("رفيقي", "السلام عليكم! أنا رفيقي. اضغط الميكروفون وتحدث، وسأنتظر حتى تنتهي ثم أجيبك.", false);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 247, 251));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(12), dp(18), dp(12));
        header.setBackgroundColor(Color.rgb(16, 24, 39));
        TextView title = text("رفيقي", 25, Color.WHITE);
        title.setTypeface(null, 1);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));
        status = text("جاهز للاستماع", 13, Color.rgb(174, 202, 218));
        header.addView(status, new LinearLayout.LayoutParams(-1, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottom.setBackgroundColor(Color.WHITE);
        micButton = button("🎙", 48);
        micButton.setContentDescription("تحدث مع رفيقي");
        bottom.addView(micButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
        input = new EditText(this);
        input.setHint("اكتب رسالة…");
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        input.setPadding(dp(12), 0, dp(12), 0);
        bottom.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button send = button("إرسال", 16);
        bottom.addView(send, new LinearLayout.LayoutParams(dp(76), dp(52)));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        micButton.setOnClickListener(v -> toggleListening());
        send.setOnClickListener(v -> sendTyped());
        input.setOnEditorActionListener((v, actionId, event) -> { sendTyped(); return true; });
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setTextDirection(View.TEXT_DIRECTION_RTL);
        return t;
    }

    private Button button(String value, int size) {
        Button b = new Button(this); b.setText(value); b.setTextSize(size); b.setAllCaps(false); return b;
    }

    private void initSpeaker() {
        speaker = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                int language = speaker.setLanguage(new Locale("ar", "SA"));
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) speaker.setLanguage(new Locale("ar"));
                speaker.setSpeechRate(.92f); speaker.setPitch(1.0f); speakerReady = true;
            }
        });
    }

    private void initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this); recognizer.setRecognitionListener(this);
        } else status.setText("التعرف الصوتي غير متوفر؛ استخدم الكتابة");
    }

    private void sendTyped() {
        String value = input.getText().toString().trim(); if (value.length() == 0) return;
        input.setText(""); respond(value);
    }

    private void respond(String value) {
        appendMessage("أنت", value, true);
        String answer = engine.reply(value);
        appendMessage("رفيقي", answer, false);
        speak(answer);
    }

    private void speak(String answer) {
        if (speakerReady && speaker != null) speaker.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "rafiki-reply");
    }

    private void toggleListening() {
        if (recognizer == null) { Toast.makeText(this, "التعرف الصوتي غير متوفر على هذا الهاتف", Toast.LENGTH_SHORT).show(); return; }
        if (listening) { recognizer.stopListening(); listening = false; status.setText("أعالج كلامك…"); micButton.setText("🎙"); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO); return; }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
        listening = true; micButton.setText("⏹"); status.setText("أستمع… تكلم براحتك وسأنتظر حتى تنتهي"); recognizer.startListening(intent);
    }

    private void appendMessage(String who, String value, boolean fromUser) {
        TextView bubble = text(who + "\n" + value, 16, fromUser ? Color.WHITE : Color.rgb(27, 39, 56));
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackgroundColor(fromUser ? Color.rgb(35, 123, 154) : Color.WHITE);
        LinearLayout row = new LinearLayout(this); row.setGravity(fromUser ? Gravity.RIGHT : Gravity.LEFT); row.setPadding(0, dp(4), 0, dp(4));
        row.addView(bubble, new LinearLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * .82f), -2));
        messages.addView(row); scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onReadyForSpeech(Bundle params) { status.setText("أستمع…"); }
    @Override public void onBeginningOfSpeech() { status.setText("أسمعك الآن…"); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }
    @Override public void onEndOfSpeech() { status.setText("انتهيت من الاستماع، أفكر…"); }
    @Override public void onEvent(int eventType, Bundle params) { }
    @Override public void onPartialResults(Bundle results) {
        ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (heard != null && !heard.isEmpty()) status.setText("أسمع: " + heard.get(0));
    }
    @Override public void onResults(Bundle results) {
        listening = false; micButton.setText("🎙"); status.setText("جاهز للاستماع");
        ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (heard != null && !heard.isEmpty() && heard.get(0).trim().length() > 0) respond(heard.get(0));
        else status.setText("لم أسمع جملة واضحة؛ حاول مرة أخرى");
    }
    @Override public void onError(int error) {
        listening = false; micButton.setText("🎙");
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) status.setText("لم أسمع كلامًا واضحًا؛ اضغط وتحدث مرة أخرى");
        else status.setText("حدثت مشكلة في التعرف الصوتي؛ يمكنك الكتابة");
    }

    @Override public void onDestroy() {
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (speaker != null) { speaker.stop(); speaker.shutdown(); speaker = null; }
        super.onDestroy();
    }
}
