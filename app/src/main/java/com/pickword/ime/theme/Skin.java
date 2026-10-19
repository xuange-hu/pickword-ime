package com.pickword.ime.theme;

/**
 * 皮肤：键盘与候选栏的全部配色。
 *
 * <p>所有颜色都是运行时数值，不依赖 XML 资源，因此可以即时切换、也能由用户挑的主色派生。
 * 预设皮肤覆盖主流输入法的几种观感：经典白、深空黑、低饱和护眼、彩色个性。
 */
public class Skin {

    public final String id;
    public final String name;

    public final int keyboardBg;
    public final int keyBg;
    public final int keyBgSpecial;
    public final int keyText;
    public final int keyTextSpecial;
    public final int candidateBg;
    public final int candidateSelected;
    public final int gloss;
    public final int star;
    public final int divider;
    public final int accent;
    public final boolean dark;

    public Skin(String id, String name, int keyboardBg, int keyBg, int keyBgSpecial,
                int keyText, int keyTextSpecial, int candidateBg, int candidateSelected,
                int gloss, int star, int divider, int accent, boolean dark) {
        this.id = id;
        this.name = name;
        this.keyboardBg = keyboardBg;
        this.keyBg = keyBg;
        this.keyBgSpecial = keyBgSpecial;
        this.keyText = keyText;
        this.keyTextSpecial = keyTextSpecial;
        this.candidateBg = candidateBg;
        this.candidateSelected = candidateSelected;
        this.gloss = gloss;
        this.star = star;
        this.divider = divider;
        this.accent = accent;
        this.dark = dark;
    }

    // ---------------- 预设 ----------------

    public static final Skin CLASSIC = new Skin(
            "classic", "经典白",
            0xFFDDE3E6, 0xFFFFFFFF, 0xFFC7D0D4,
            0xFF1B1F21, 0xFF1B1F21,
            0xFFF7F9FA, 0xFFDCEFE9,
            0xFF0E7C66, 0xFFE0A32E, 0xFFD5DBDE, 0xFF0E7C66, false);

    public static final Skin DARK = new Skin(
            "dark", "深空黑",
            0xFF0C0F10, 0xFF262B2E, 0xFF16191B,
            0xFFE6EAEB, 0xFFB7C0C4,
            0xFF1A1E20, 0xFF1E3630,
            0xFF6FD3B4, 0xFFE6B94F, 0xFF2A3033, 0xFF4FBF9E, true);

    public static final Skin GREEN = new Skin(
            "green", "拾词绿",
            0xFFD6E9E2, 0xFFFFFFFF, 0xFFB9D5CA,
            0xFF14312A, 0xFF14312A,
            0xFFEAF5F1, 0xFFBFE3D4,
            0xFF0E7C66, 0xFFD99A17, 0xFFC2D6CD, 0xFF0E7C66, false);

    public static final Skin PINK = new Skin(
            "pink", "樱花粉",
            0xFFF3DFE6, 0xFFFFFFFF, 0xFFE9C6D2,
            0xFF3B2430, 0xFF3B2430,
            0xFFFBF0F4, 0xFFF7CFDD,
            0xFFD63A6A, 0xFFE0A32E, 0xFFE6CED7, 0xFFE75480, false);

    public static final Skin OCEAN = new Skin(
            "ocean", "深海蓝",
            0xFF0E1A2B, 0xFF20344D, 0xFF142742,
            0xFFE3EBF5, 0xFFA9BBCF,
            0xFF16283F, 0xFF1E3F63,
            0xFF6FB6F0, 0xFFE6B94F, 0xFF263C55, 0xFF4A9BE0, true);

    public static final Skin PAPER = new Skin(
            "paper", "羊皮纸",
            0xFFE7DCCB, 0xFFFBF6EC, 0xFFDFD2BE,
            0xFF3E2F23, 0xFF3E2F23,
            0xFFFBF6EC, 0xFFE8D9C3,
            0xFF8D6E63, 0xFFC08A2E, 0xFFD9CBBA, 0xFF8D6E63, false);

    private static final Skin[] PRESETS = {CLASSIC, DARK, GREEN, PINK, OCEAN, PAPER};

    public static Skin[] presets() {
        return PRESETS;
    }

    public static Skin byId(String id) {
        if (id != null) {
            for (Skin s : PRESETS) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
        }
        return CLASSIC;
    }

    /** 由用户选择的主色派生一套皮肤（自定义） */
    public static Skin fromAccent(String name, int accent, boolean dark) {
        int opaque = 0xFF000000 | (accent & 0x00FFFFFF);
        int keyboardBg = dark ? darken(opaque, 0.72f) : lighten(opaque, 0.80f);
        int keyBg = dark ? lighten(opaque, 0.14f) : 0xFFFFFFFF;
        int keySpecial = dark ? darken(opaque, 0.58f) : lighten(opaque, 0.70f);
        int keyText = dark ? 0xFFE9EEF1 : 0xFF16191B;
        int candidateBg = dark ? darken(opaque, 0.66f) : lighten(opaque, 0.90f);
        int candidateSelected = dark ? mix(darken(opaque, 0.45f), opaque, 0.35f)
                : lighten(opaque, 0.70f);
        int gloss = dark ? lighten(opaque, 0.35f) : darken(opaque, 0.10f);
        int divider = dark ? 0x26FFFFFF : 0x1A000000;
        return new Skin("custom", name, keyboardBg, keyBg, keySpecial, keyText,
                mix(keyText, keyBg, 0.35f), candidateBg, candidateSelected, gloss,
                0xFFE0A32E, divider, opaque, dark);
    }

    // ---------------- 颜色工具 ----------------

    /** 线性混合两种 ARGB 颜色 */
    public static int mix(int a, int b, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int ra = aa + Math.round((ba - aa) * r);
        int rr = ar + Math.round((br - ar) * r);
        int rg = ag + Math.round((bg - ag) * r);
        int rb = ab + Math.round((bb - ab) * r);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }

    public static int darken(int color, float amount) {
        return mix(color, 0xFF000000, amount);
    }
}
