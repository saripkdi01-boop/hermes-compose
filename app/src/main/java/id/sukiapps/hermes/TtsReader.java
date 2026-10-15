package id.sukiapps.hermes;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v8.0: Text-to-Speech per pesan (bahasa Indonesia).
 * - Inisialisasi lazy (baru saat user pertama kali menekan "Bacakan").
 * - Tanpa permission baru, tanpa library (framework android.speech.tts).
 * - Helper splitForSpeech() & stripMarkdown() murni Java -> unit-testable di JVM.
 */
public class TtsReader {

    public interface ReadyCb { void onReady(boolean ok); }

    private TextToSpeech tts;
    private boolean ready;
    private boolean speaking;
    private final List<ReadyCb> pending = new ArrayList<ReadyCb>();

    /** Inisialisasi lazy; callback dipanggil di thread pemanggil listener (UI thread). */
    public synchronized void ensure(Context ctx, ReadyCb cb) {
        if (ready) { cb.onReady(true); return; }
        pending.add(cb);
        if (tts != null) return; // init sedang berjalan
        try {
            final Context app = ctx.getApplicationContext();
            tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
                public void onInit(int status) {
                    boolean ok = false;
                    if (status == TextToSpeech.SUCCESS) {
                        try {
                            int r = tts.setLanguage(new Locale("id", "ID"));
                            if (r == TextToSpeech.LANG_MISSING_DATA
                                    || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                                // Fallback: bahasa default perangkat.
                                r = tts.setLanguage(Locale.getDefault());
                            }
                            ok = r != TextToSpeech.LANG_MISSING_DATA
                                    && r != TextToSpeech.LANG_NOT_SUPPORTED;
                            tts.setOnUtteranceProgressListener(
                                    new UtteranceProgressListener() {
                                        public void onStart(String id) {}
                                        public void onDone(String id) { onUtteranceEnd(); }
                                        public void onError(String id) { onUtteranceEnd(); }
                                    });
                        } catch (Exception ignored) { ok = false; }
                    }
                    List<ReadyCb> cbs;
                    synchronized (TtsReader.this) {
                        ready = ok;
                        cbs = new ArrayList<ReadyCb>(pending);
                        pending.clear();
                    }
                    for (ReadyCb c : cbs) {
                        try { c.onReady(ok); } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception e) {
            tts = null;
            List<ReadyCb> cbs = new ArrayList<ReadyCb>(pending);
            pending.clear();
            for (ReadyCb c : cbs) {
                try { c.onReady(false); } catch (Exception ignored) {}
            }
        }
    }

    private synchronized void onUtteranceEnd() {
        speaking = false;
    }

    public synchronized boolean isSpeaking() {
        return speaking && tts != null;
    }

    public synchronized boolean isReady() {
        return ready && tts != null;
    }

    /** Bacakan teks (markdown dibersihkan dulu). Potong per kalimat agar TTS stabil. */
    public synchronized void speak(String text) {
        if (!ready || tts == null) return;
        try { tts.stop(); } catch (Exception ignored) {}
        List<String> chunks = splitForSpeech(stripMarkdown(text), 3900);
        if (chunks.isEmpty()) return;
        try {
            for (int i = 0; i < chunks.size(); i++) {
                int mode = (i == 0) ? TextToSpeech.QUEUE_FLUSH
                        : TextToSpeech.QUEUE_ADD;
                tts.speak(chunks.get(i), mode, null, "hermes" + i);
            }
            speaking = true;
        } catch (Exception e) {
            speaking = false;
        }
    }

    public synchronized void stop() {
        speaking = false;
        try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
    }

    public synchronized void shutdown() {
        speaking = false;
        ready = false;
        pending.clear();
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
        tts = null;
    }

    /**
     * Bersihkan markdown agar enak dibacakan: blok kode -> "[blok kode]",
     * hapus bold/italic/heading/quote/list marker, link -> teks saja.
     * Murni Java (unit-testable).
     */
    public static String stripMarkdown(String md) {
        if (md == null) return "";
        String s = md;
        // Blok kode ```...``` -> placeholder singkat.
        s = Pattern.compile("```[\\w:.-]*\\n.*?```", Pattern.DOTALL)
                .matcher(s).replaceAll(" [blok kode] ");
        // Inline code `x` -> x
        s = Pattern.compile("`([^`\\n]+)`").matcher(s).replaceAll("$1");
        // Link [teks](url) -> teks
        s = Pattern.compile("\\[([^\\]]+)\\]\\([^\\)]+\\)")
                .matcher(s).replaceAll("$1");
        // Heading ####, quote >, list -/* / 1.
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.replaceAll("^#{1,6}\\s+", "")
                    .replaceAll("^>\\s?", "")
                    .replaceAll("^\\s*([-*+]|\\d+[.)])\\s+", "");
            sb.append(t).append('\n');
        }
        s = sb.toString();
        // Bold/italic ** __ * _
        s = s.replaceAll("(\\*\\*|__)(.*?)\\1", "$2");
        s = s.replaceAll("(^|\\W)\\*([^\\*\\n]+)\\*", "$1$2");
        s = s.replaceAll("(^|\\W)_([^_\\n]+)_", "$1$2");
        // Rapikan whitespace.
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n").trim();
        return s;
    }

    /**
     * Potong teks menjadi chunk <= maxLen, diutamakan pada batas kalimat,
     * lalu batas kata, terakhir potong paksa. Murni Java (unit-testable).
     */
    public static List<String> splitForSpeech(String text, int maxLen) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        String rest = text.trim();
        Pattern sentenceEnd = Pattern.compile("[.!?…\\n]+");
        while (!rest.isEmpty()) {
            if (rest.length() <= maxLen) {
                out.add(rest);
                break;
            }
            int cut = -1;
            Matcher m = sentenceEnd.matcher(rest);
            int lastOk = -1;
            while (m.find() && m.end() <= maxLen) lastOk = m.end();
            if (lastOk > maxLen / 4) {
                cut = lastOk;
            } else {
                int sp = rest.lastIndexOf(' ', maxLen);
                cut = (sp > maxLen / 4) ? sp + 1 : maxLen;
            }
            out.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        return out;
    }
}
