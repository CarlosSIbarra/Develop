package com.rafiki.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
    private final Handler handler = new Handler();
    private boolean listening;
    private boolean voiceConversation;
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
        TextView title = text("رفيقي  •  مساعدك الصوتي", 24, Color.WHITE);
        title.setTypeface(null, 1);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));
        status = text("جاهز للاستماع", 13, Color.rgb(174, 202, 218));
        header.addView(status, new LinearLayout.LayoutParams(-1, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout quick = new LinearLayout(this);
        quick.setGravity(Gravity.CENTER);
        quick.setPadding(dp(8), dp(6), dp(8), dp(2));
        quick.setBackgroundColor(Color.rgb(244, 247, 251));
        String[] suggestions = {"ماذا تستطيع؟", "كم الساعة؟", "قل نكتة"};
        for (String suggestion : suggestions) {
            Button chip = button(suggestion, 12);
            chip.setTextColor(Color.rgb(27, 39, 56));
            chip.setBackground(round(Color.WHITE, dp(18), Color.rgb(213, 224, 235)));
            chip.setOnClickListener(v -> respond(suggestion));
            quick.addView(chip, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        root.addView(quick, new LinearLayout.LayoutParams(-1, dp(52)));

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
        micButton = button("🎙", 25);
        micButton.setContentDescription("تحدث مع رفيقي");
        micButton.setTextColor(Color.WHITE);
        micButton.setBackground(round(Color.rgb(35, 123, 154), dp(18), Color.TRANSPARENT));
        bottom.addView(micButton, new LinearLayout.LayoutParams(dp(52), dp(52)));
        input = new EditText(this);
        input.setHint("اكتب رسالة…");
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(round(Color.rgb(244, 247, 251), dp(18), Color.rgb(218, 229, 239)));
        bottom.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1f));
        Button send = button("إرسال", 16);
        send.setTextColor(Color.rgb(27, 39, 56));
        send.setBackground(round(Color.rgb(82, 214, 177), dp(18), Color.TRANSPARENT));
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
        speaker.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) {
                if ("rafiki-reply".equals(id)) handler.postDelayed(() -> { if (voiceConversation && !listening) startListeningInternal(); }, 450);
            }
            @Override public void onError(String id) { }
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
        else if (voiceConversation) handler.postDelayed(() -> { if (voiceConversation && !listening) startListeningInternal(); }, 500);
    }

    private void toggleListening() {
        if (recognizer == null) { Toast.makeText(this, "التعرف الصوتي غير متوفر على هذا الهاتف", Toast.LENGTH_SHORT).show(); return; }
        if (voiceConversation || listening) { stopVoiceConversation(); return; }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO); return; }
        voiceConversation = true;
        startListeningInternal();
    }

    private void startListeningInternal() {
        if (!voiceConversation || recognizer == null || listening) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
        listening = true; micButton.setText("⏹"); status.setText("محادثة صوتية: أستمع… تكلم براحتك"); recognizer.startListening(intent);
    }

    private void stopVoiceConversation() {
        voiceConversation = false; listening = false;
        if (recognizer != null) recognizer.cancel();
        if (speaker != null) speaker.stop();
        micButton.setText("🎙"); status.setText("توقفت المحادثة الصوتية — اضغط 🎙 للبدء");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_AUDIO && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            voiceConversation = true; startListeningInternal();
        } else if (requestCode == REQ_AUDIO) {
            status.setText("أحتاج إذن الميكروفون للمحادثة الصوتية");
        }
    }

    private void appendMessage(String who, String value, boolean fromUser) {
        TextView bubble = text(who + "\n" + value, 15, fromUser ? Color.WHITE : Color.rgb(27, 39, 56));
        bubble.setIncludeFontPadding(false);
        bubble.setLineSpacing(0, 1.08f);
        bubble.setPadding(dp(16), dp(12), dp(16), dp(12));
        bubble.setBackground(round(fromUser ? Color.rgb(35, 123, 154) : Color.WHITE, dp(20), fromUser ? Color.TRANSPARENT : Color.rgb(224, 231, 239)));
        LinearLayout row = new LinearLayout(this); row.setGravity(fromUser ? Gravity.RIGHT : Gravity.LEFT); row.setPadding(0, dp(4), 0, dp(4));
        row.addView(bubble, new LinearLayout.LayoutParams((int)(getResources().getDisplayMetrics().widthPixels * .82f), -2));
        messages.addView(row); scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill); drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
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
        listening = false; status.setText(voiceConversation ? "أجهز الرد الصوتي…" : "جاهز للاستماع");
        ArrayList<String> heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (heard != null && !heard.isEmpty() && heard.get(0).trim().length() > 0) respond(heard.get(0));
        else if (voiceConversation) handler.postDelayed(this::startListeningInternal, 650);
        else status.setText("لم أسمع جملة واضحة؛ حاول مرة أخرى");
    }
    @Override public void onError(int error) {
        listening = false;
        if (voiceConversation && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
            status.setText("لم أسمع بوضوح؛ أستمع من جديد…"); handler.postDelayed(this::startListeningInternal, 700);
        } else if (!voiceConversation) { micButton.setText("🎙"); status.setText("لم أسمع كلامًا واضحًا؛ اضغط وتحدث مرة أخرى"); }
        else { voiceConversation = false; micButton.setText("🎙"); status.setText("حدثت مشكلة في التعرف الصوتي؛ يمكنك الكتابة"); }
    }

    @Override public void onDestroy() {
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        if (speaker != null) { speaker.stop(); speaker.shutdown(); speaker = null; }
        super.onDestroy();
    }
}
