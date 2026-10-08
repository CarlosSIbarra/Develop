package com.rafiki.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssistantEngine {
    private final SharedPreferences memory;
    private final Random random = new Random();
    private final String[] greetings = {"وعليكم السلام ورحمة الله وبركاته!", "وعليكم السلام! أهلًا بك، كيف أساعدك؟", "وعليكم السلام يا صديقي، أنا جاهز."};
    private final String[] jokes = {"مرة جهاز كمبيوتر ذهب للطبيب، قال له الطبيب: يبدو أن عندك فيروسًا!", "لماذا تأخر الروبوت؟ لأنه كان يحتاج إلى تحديث صغير.", "قال الهاتف للشاحن: أنت مصدر سعادتي وطاقة حياتي."};

    public AssistantEngine(Context context) { memory = context.getSharedPreferences("rafiki_memory", Context.MODE_PRIVATE); }

    public String reply(String original) {
        String input = normalize(original);
        if (input.length() == 0) return "لم أسمع شيئًا. تحدث معي أو اكتب ما تريد.";

        Matcher name = Pattern.compile("(?:اسمي|انا اسمي)\\s+(.+)").matcher(input);
        if (name.find()) {
            String value = name.group(1).trim();
            if (value.length() > 30) value = value.substring(0, 30);
            memory.edit().putString("name", value).apply();
            return "تشرفت بك يا " + value + ". سأحفظ اسمك على هذا الهاتف.";
        }
        if (has(input, "ما اسمي", "تذكر اسمي", "هل تعرف اسمي")) {
            String value = memory.getString("name", "");
            return value.length() == 0 ? "لم تخبرني باسمك بعد. قل: اسمي ثم اسمك." : "اسمك هو " + value + ".";
        }
        if (has(input, "السلام عليكم", "سلام عليكم", "مرحبا", "اهلا", "هلا", "صباح الخير", "مساء الخير", "يا رفيقي")) return greetings[random.nextInt(greetings.length)];
        if (has(input, "كيف حالك", "كيفك", "شلونك", "عامل ايه", "كيف انت")) return "أنا بخير وسعيد بالكلام معك. وأنت كيف حالك؟";
        if (has(input, "ماذا تستطيع", "وش تقدر", "ايش تقدر", "ماذا يمكنك", "قدراتك", "ساعدني")) return "أستطيع التحدث معك بالصوت أو الكتابة، معرفة الوقت والتاريخ، حفظ اسمك، قول نكتة، إجراء حساب بسيط، وشرح طريقة استخدامي.";
        if (has(input, "من انت", "ما اسمك", "اسمك ايش", "عرف نفسك")) return "أنا رفيقي، مساعد صوتي عربي خفيف. أعمل داخل الهاتف دون مفتاح API وأتعلم تفضيلات بسيطة منك.";
        if (has(input, "الوقت", "الساعة", "كم الساعة")) return "الوقت الآن " + new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Calendar.getInstance().getTime()) + ".";
        if (has(input, "التاريخ", "اي يوم", "اليوم كم")) return "تاريخ اليوم هو " + new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("ar")).format(Calendar.getInstance().getTime()) + ".";
        if (has(input, "نكتة", "نكته", "نكت", "قول نكته", "قل نكته", "اضحكني", "شيء مضحك")) return jokes[random.nextInt(jokes.length)];
        if (has(input, "شكرا", "مشكور", "يعطيك العافية", "تسلم")) return "العفو، هذا واجبي. أنا موجود متى احتجتني.";
        if (has(input, "مع السلامة", "باي", "تصبح على خير", "اشوفك")) return "مع السلامة! أتمنى لك يومًا جميلًا.";
        if (has(input, "شجعني", "كلمة تحفيز", "انا تعبان", "اشعر بالحزن")) return "خذ نفسًا هادئًا، وابدأ بخطوة صغيرة. أنت قادر على التقدم.";
        if (has(input, "احفظ ان", "تذكر ان", "لا تنس ان")) {
            String note = input.replaceFirst("^(احفظ ان|تذكر ان|لا تنس ان)\\s*", "").trim();
            if (note.length() > 0) memory.edit().putString("note", note).apply();
            return "حسنًا، حفظت هذه المعلومة في ذاكرتي المحلية.";
        }
        if (has(input, "ماذا حفظت", "ما الذي حفظته", "ذاكرتك")) {
            String note = memory.getString("note", "");
            return note.length() == 0 ? "لا توجد معلومة محفوظة حتى الآن." : "حفظت أنك قلت: " + note + ".";
        }
        String calculation = calculate(input);
        if (calculation != null) return calculation;
        if (has(input, "كيف استخدمك", "تعليمات", "مساعدة", "الاوامر")) return "اضغط الميكروفون وتكلم حتى تنتهي، أو اكتب رسالتك. جرّب: السلام عليكم، كيف حالك، كم الساعة، ما اسمك، ماذا تستطيع، أو احسب 12 + 7.";
        return "فهمت أنك قلت: «" + original.trim() + "». ما زلت أتعلم هذه الجملة. جرّب أن تسألني عن الوقت أو قدراتي، أو قل: ماذا تستطيع؟";
    }

    private String calculate(String input) {
        Matcher m = Pattern.compile("(?:احسب\\s*)?([0-9]+)\\s*([+\\-*/])\\s*([0-9]+)").matcher(input);
        if (!m.find()) return null;
        try {
            double a = Double.parseDouble(m.group(1)), b = Double.parseDouble(m.group(3)), value;
            switch (m.group(2).charAt(0)) { case '+': value = a + b; break; case '-': value = a - b; break; case '*': value = a * b; break; default: if (b == 0) return "لا يمكن القسمة على صفر."; value = a / b; }
            return "الناتج هو " + (value == Math.rint(value) ? String.valueOf((long)value) : String.valueOf(value)) + ".";
        } catch (Exception ignored) { return null; }
    }

    private boolean has(String value, String... phrases) { for (String phrase : phrases) if (value.contains(phrase)) return true; return false; }

    private String normalize(String value) {
        String s = value == null ? "" : value.toLowerCase(new Locale("ar")).trim();
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي');
        s = s.replaceAll("[،,!.؟?؛:()\\[\\]{}]", " ").replaceAll("\\s+", " ");
        return s;
    }
}
