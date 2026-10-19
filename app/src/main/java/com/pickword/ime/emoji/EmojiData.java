package com.pickword.ime.emoji;

/** Emoji 与颜文字数据（内置，离线可用）。分类与主流输入法一致。 */
public final class EmojiData {

    public static final String[] CATEGORIES = {
            "常用", "表情", "手势", "动物", "食物", "活动", "旅行", "符号", "颜文字"
    };

    private static final String[] RECENT = {
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDE14", "\uD83D\uDE22",
            "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83D\uDE24", "\uD83D\uDE33", "\uD83D\uDE44",
            "\uD83D\uDC4D", "\uD83D\uDC4F", "\uD83D\uDE4F", "\uD83D\uDCAA", "\u2764\uFE0F",
            "\uD83D\uDD25", "\uD83C\uDF89", "\uD83C\uDF1F", "\uD83D\uDE80", "\uD83D\uDCAF",
    };

    private static final String[] SMILEY = {
            "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE03",
            "\uD83D\uDE04", "\uD83D\uDE05", "\uD83D\uDE06", "\uD83D\uDE09", "\uD83D\uDE0A",
            "\uD83D\uDE0B", "\uD83D\uDE0E", "\uD83D\uDE0D", "\uD83D\uDE18", "\uD83D\uDE1D",
            "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83D\uDE1A", "\uD83D\uDE10", "\uD83D\uDE11",
            "\uD83D\uDE36", "\uD83D\uDE0F", "\uD83D\uDE12", "\uD83D\uDE44", "\uD83D\uDE2E",
            "\uD83E\uDD14", "\uD83D\uDE14", "\uD83D\uDE41", "\uD83D\uDE15", "\uD83D\uDE23",
            "\uD83D\uDE2B", "\uD83D\uDE29", "\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE24",
            "\uD83D\uDE21", "\uD83D\uDE20", "\uD83D\uDE33", "\uD83D\uDE31", "\uD83D\uDE28",
            "\uD83E\uDD29", "\uD83E\uDD2A", "\uD83E\uDD2B", "\uD83D\uDE34", "\uD83E\uDD24",
            "\uD83D\uDE37", "\uD83E\uDD12", "\uD83E\uDD15", "\uD83E\uDD22", "\uD83E\uDD2E",
            "\uD83E\uDD27", "\uD83D\uDE08", "\uD83D\uDC7F", "\uD83D\uDC80", "\uD83D\uDCA9",
    };

    private static final String[] GESTURE = {
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDC4A", "\u270A",
            "\u270B", "\u270C\uFE0F", "\uD83D\uDE4F", "\uD83D\uDC4C", "\uD83E\uDD1E",
            "\uD83E\uDD1D", "\uD83E\uDD1F", "\uD83D\uDE4C", "\uD83D\uDE4B", "\uD83D\uDE47",
            "\uD83D\uDE46", "\uD83D\uDC85", "\uD83D\uDCAA", "\uD83D\uDCAA", "\uD83D\uDC40",
            "\uD83E\uDDB9", "\uD83D\uDE4D", "\uD83E\uDD26", "\uD83D\uDE45", "\uD83D\uDC4B",
            "\uD83D\uDE4A", "\uD83D\uDC48", "\uD83D\uDC49", "\uD83D\uDC46", "\uD83D\uDC47",
            "\u261D\uFE0F", "\uD83D\uDC41\uFE0F", "\uD83E\uDD11", "\uD83D\uDD95", "\uD83E\uDD19",
    };

    private static final String[] ANIMAL = {
            "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83E\uDD81",
            "\uD83D\uDC35", "\uD83D\uDC30", "\uD83D\uDC2F", "\uD83D\uDC28", "\uD83D\uDC3B",
            "\uD83D\uDC3A", "\uD83D\uDC18", "\uD83D\uDC11", "\uD83D\uDC10", "\uD83D\uDC14",
            "\uD83D\uDC26", "\uD83D\uDC25", "\uD83D\uDC27", "\uD83D\uDC2B", "\uD83D\uDC13",
            "\uD83D\uDC1B", "\uD83D\uDC1D", "\uD83D\uDC1F", "\uD83D\uDC20", "\uD83D\uDC2C",
            "\uD83D\uDC0D", "\uD83D\uDC22", "\uD83D\uDC19", "\uD83D\uDC17", "\uD83D\uDC16",
            "\uD83D\uDC03", "\uD83D\uDC0B", "\uD83D\uDC12", "\uD83D\uDC33", "\uD83D\uDC2A",
            "\uD83E\uDD84", "\uD83D\uDC3C", "\uD83D\uDC2E", "\uD83E\uDD8C", "\uD83E\uDD97",
    };

    private static final String[] FOOD = {
            "\uD83C\uDF5A", "\uD83C\uDF5B", "\uD83C\uDF5C", "\uD83C\uDF5D", "\uD83C\uDF54",
            "\uD83C\uDF5F", "\uD83C\uDF55", "\uD83C\uDF2D", "\uD83C\uDF5E", "\uD83C\uDF73",
            "\uD83C\uDF72", "\uD83C\uDF63", "\uD83C\uDF57", "\uD83C\uDF56", "\uD83C\uDF64",
            "\uD83C\uDF71", "\uD83C\uDF65", "\uD83C\uDF61", "\uD83C\uDF59", "\uD83C\uDF58",
            "\uD83C\uDF62", "\uD83C\uDF74", "\uD83C\uDF75", "\uD83C\uDF7A", "\uD83C\uDF7B",
            "\uD83C\uDF78", "\uD83C\uDF79", "\uD83E\uDD5A", "\uD83C\uDF4E", "\uD83C\uDF4F",
            "\uD83C\uDF4A", "\uD83C\uDF4B", "\uD83C\uDF4C", "\uD83C\uDF49", "\uD83C\uDF47",
            "\uD83C\uDF51", "\uD83C\uDF53", "\uD83C\uDF50", "\uD83C\uDF52", "\uD83C\uDF45",
            "\uD83C\uDF46", "\uD83C\uDF60", "\uD83C\uDF6F", "\uD83C\uDF70", "\uD83C\uDF6D",
    };

    private static final String[] ACTIVITY = {
            "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFC8", "\uD83C\uDFD2", "\uD83C\uDFBE",
            "\uD83C\uDFD1", "\uD83C\uDFD3", "\uD83E\uDD4A", "\uD83C\uDFAF", "\uD83C\uDFB1",
            "\uD83C\uDFB3", "\uD83C\uDFAE", "\uD83C\uDFB5", "\uD83C\uDFB9", "\uD83C\uDFBA",
            "\uD83C\uDFBB", "\uD83E\uDD41", "\uD83C\uDFAC", "\uD83C\uDFA8", "\uD83C\uDFAD",
            "\uD83D\uDD79\uFE0F", "\uD83D\uDC68\u200D\uD83C\uDFA8", "\uD83C\uDFC4",
            "\uD83D\uDEB5", "\uD83E\uDD38", "\uD83D\uDC6B", "\uD83D\uDC6F", "\uD83C\uDFCB\uFE0F",
            "\uD83E\uDD3C", "\uD83E\uDDD8", "\uD83D\uDD74\uFE0F", "\uD83E\uDDD9", "\uD83D\uDEB4",
            "\uD83D\uDEB6", "\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67", "\uD83C\uDF1F",
            "\uD83C\uDFAB", "\uD83D\uDCAF", "\uD83C\uDFC6", "\uD83C\uDF96\uFE0F", "\uD83C\uDFC5",
    };

    private static final String[] TRAVEL = {
            "\uD83D\uDE97", "\uD83D\uDE95", "\uD83D\uDE99", "\uD83D\uDE8C", "\uD83D\uDEB2",
            "\uD83D\uDEF5", "\u2708\uFE0F", "\uD83D\uDEEB", "\uD83D\uDEA2", "\u26F5\uFE0F",
            "\uD83D\uDEA4", "\uD83D\uDE80", "\uD83D\uDEF0", "\uD83D\uDE82", "\uD83D\uDE8E",
            "\uD83D\uDE81", "\uD83D\uDE86", "\uD83D\uDE84", "\uD83D\uDE9D", "\uD83D\uDE91",
            "\uD83C\uDFE0", "\uD83C\uDFE1", "\uD83C\uDFE2", "\uD83C\uDFE8", "\uD83C\uDFEC",
            "\uD83C\uDFE5", "\uD83D\uDDFC", "\uD83C\uDFD7\uFE0F", "\uD83C\uDFEF", "\uD83C\uDFD9\uFE0F",
            "\uD83C\uDF0B", "\uD83C\uDF04", "\uD83C\uDF06", "\uD83C\uDFD4\uFE0F", "\uD83C\uDF0F",
            "\uD83C\uDF0D", "\uD83C\uDF0E", "\uD83D\uDDFA", "\uD83E\uDDED", "\uD83C\uDF03",
            "\uD83C\uDF07", "\u2601\uFE0F", "\uD83C\uDF29\uFE0F", "\u26C5", "\uD83C\uDF2B\uFE0F",
    };

    private static final String[] SYMBOL = {
            "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9C", "\uD83D\uDC9A",
            "\uD83D\uDC99", "\uD83D\uDC9E", "\uD83D\uDC94", "\uD83D\uDC96", "\uD83D\uDC97",
            "\uD83D\uDC98", "\uD83D\uDC93", "\uD83D\uDC95", "\uD83D\uDC9F", "\uD83D\uDDA4",
            "\uD83D\uDD25", "\u2728", "\u2B50", "\uD83C\uDF1F", "\uD83D\uDCA5", "\uD83D\uDCA6",
            "\uD83D\uDCA7", "\uD83D\uDCA8", "\uD83C\uDF43", "\uD83C\uDF39", "\uD83C\uDF38",
            "\uD83C\uDF37", "\uD83C\uDF3B", "\uD83C\uDF3A", "\uD83C\uDF41", "\uD83C\uDF40",
            "\u2705", "\u274C", "\u2753", "\u2757", "\u203C\uFE0F", "\uD83D\uDCAF", "\uD83D\uDCA0",
            "\uD83D\uDCAB", "\uD83D\uDCCC", "\uD83C\uDF88", "\uD83C\uDF89", "\uD83C\uDF8A",
            "\uD83C\uDF81", "\uD83C\uDF8F", "\uD83C\uDFA9", "\uD83C\uDF80", "\u26A1", "\uD83D\uDD31",
    };

    private static final String[] KAOMOJI = {
            "(￣▽)", "(´･ω･`)", "(；一_一)", "(*/ω＼*)", "(๑•̀ㅂ•́)و✧",
            "o(*≧▽≦)ツ", "(＾▽＾)", "(T_T)", "(╯°□°）╯︵ ┻━", "┬─┬ノ( º _ ºノ)",
            "(￣ε(#￣)☆╰╮(￣▽///)", "(*^▽^*)", "(°∀°)ﾉ", "ヽ(´▽`)/", "(*´∀`)~♥",
            "(-_-)zzZ", "(；′⌒`)", "Σ(￣□￣||)", "(⌐■_■)", "ʕ•ᴥ•ʔ",
            "(*≧ω≦)", "(ノ°ο°)ノ", "(^_^)/~~", "Orz", "(′▽`〃)",
            "φ(≧ω*)♪", "(*ノ・ω・）", "ヽ(°▽、°)ﾉ", "(ーー;)", "(￣工￣lll)",
    };

    private static final String[][] GROUPS = {
            RECENT, SMILEY, GESTURE, ANIMAL, FOOD, ACTIVITY, TRAVEL, SYMBOL, KAOMOJI
    };

    private EmojiData() {
    }

    public static String[] of(int category) {
        if (category < 0 || category >= GROUPS.length) {
            return RECENT;
        }
        return GROUPS[category];
    }
}
