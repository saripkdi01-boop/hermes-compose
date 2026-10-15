package id.sukiapps.hermes;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Toast;

/** Hermes Chat Lite v2.0 — tema & helper UI. */
public class Ui {

    public static final String PREFS = "hermes_chat";
    public static final String K_API = "api_key";
    public static final String K_BASE = "base_url";
    public static final String K_MODEL = "model";
    public static final String K_PERSONA = "persona";
    public static final String DEFAULT_BASE = "https://je.jerouter.web.id/v1";

    // Palet premium: gelap + amber.
    public static final int C_BG      = 0xFF0B0E13;
    public static final int C_HEADER  = 0xFF131924;
    public static final int C_CARD    = 0xFF151C28;
    public static final int C_BUBBLE  = 0xFF1B2330;
    public static final int C_INPUT   = 0xFF1B2330;
    public static final int C_ACCENT  = 0xFFF5B43C;
    public static final int C_ACCENT_D= 0xFF1A1D24; // teks di atas amber
    public static final int C_DIM     = 0xFF9AA3B2;
    public static final int C_HINT    = 0xFF5A6472;

    public static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Bubble chat; radius tiap sudut (px): tl, tr, br, bl. */
    public static GradientDrawable bubble(int color, float tl, float tr, float br, float bl) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return d;
    }

    public static GradientDrawable rounded(int color, int radiusDp, Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, radiusDp));
        return d;
    }

    public static GradientDrawable outline(int strokeColor, int radiusDp, Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setStroke(dp(ctx, 1), strokeColor);
        d.setCornerRadius(dp(ctx, radiusDp));
        return d;
    }

    public static void toast(Context ctx, String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String baseUrl(Context ctx) {
        String b = prefs(ctx).getString(K_BASE, DEFAULT_BASE);
        if (b == null || b.trim().isEmpty()) b = DEFAULT_BASE;
        b = b.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    public static String defaultPersona() {
        return "Kamu adalah Hermes, asisten AI yang ramah dan membantu. " +
               "Jawablah dalam Bahasa Indonesia yang natural dan ringkas.";
    }

    public static String persona(Context ctx) {
        String p = prefs(ctx).getString(K_PERSONA, "");
        if (p == null || p.trim().isEmpty()) return defaultPersona();
        return p.trim();
    }
}
