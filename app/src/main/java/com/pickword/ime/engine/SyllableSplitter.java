package com.pickword.ime.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 拼音音节切分器。
 *
 * <p>把连续的字母串（如 "woshi"）切成合法的普通话音节序列（"wo" + "shi"），
 * 用动态规划枚举全部切分方案，供候选查询使用。切分方案数上限 8 个，避免组合爆炸。
 */
public final class SyllableSplitter {

    /** 普通话无调音节表（含 ü 的 v 写法与少量口语音节） */
    private static final String[] SYLLABLES = {
            "a", "ai", "an", "ang", "ao",
            "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao",
            "bie", "bin", "bing", "bo", "bu",
            "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan",
            "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua",
            "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu",
            "cuan", "cui", "cun", "cuo",
            "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia",
            "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui",
            "dun", "duo",
            "e", "ei", "en", "eng", "er",
            "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
            "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou",
            "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
            "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou",
            "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
            "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu",
            "ju", "juan", "jue", "jun",
            "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou",
            "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
            "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian",
            "liang", "liao", "lie", "lin", "ling", "liu", "long", "lou", "lu", "luan",
            "lun", "luo", "lv",
            "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian",
            "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
            "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ng", "ni",
            "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu",
            "nuan", "nuo", "nv",
            "o", "ou",
            "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao",
            "pie", "pin", "ping", "po", "pou", "pu",
            "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu",
            "qu", "quan", "que", "qun",
            "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "rua",
            "ruan", "rui", "run", "ruo",
            "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan",
            "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua",
            "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su",
            "suan", "sui", "sun", "suo",
            "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie",
            "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
            "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
            "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu",
            "xu", "xuan", "xue", "xun",
            "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you",
            "yu", "yuan", "yue", "yun",
            "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai",
            "zhan", "zhang", "zhao", "zhe", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu",
            "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong",
            "zou", "zu", "zuan", "zui", "zun", "zuo",
    };

    private static final Set<String> SET = new HashSet<>(Arrays.asList(SYLLABLES));
    private static final int MAX_SPLITS = 8;
    private static final int MAX_SYLLABLE_LEN = 6;

    /** 是否为合法普通话音节（供模糊音过滤无效组合使用） */
    public static boolean isSyllable(String s) {
        return s != null && SET.contains(s);
    }

    private SyllableSplitter() {
    }

    /**
     * 切分入口。
     *
     * @param raw 纯小写字母串
     * @return 每个元素是一个音节的切分方案（按「音節数少」优先，符合整词优先直觉）
     */
    public static List<List<String>> split(String raw) {
        List<List<String>> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        int n = raw.length();
        // dp[i] 保存所有从位置 i 到末尾的切分方案
        @SuppressWarnings("unchecked")
        List<List<String>>[] dp = new ArrayList[n + 1];
        dp[n] = new ArrayList<>();
        dp[n].add(new ArrayList<String>());
        for (int i = n - 1; i >= 0; i--) {
            dp[i] = new ArrayList<>();
            int maxEnd = Math.min(n, i + MAX_SYLLABLE_LEN);
            for (int j = maxEnd; j > i; j--) {
                String syl = raw.substring(i, j);
                if (!SET.contains(syl)) {
                    continue;
                }
                for (List<String> tail : dp[j]) {
                    List<String> one = new ArrayList<>(tail.size() + 1);
                    one.add(syl);
                    one.addAll(tail);
                    dp[i].add(one);
                    if (dp[i].size() >= MAX_SPLITS) {
                        break;
                    }
                }
                if (dp[i].size() >= MAX_SPLITS) {
                    break;
                }
            }
        }
        List<List<String>> all = dp[0];
        // 只保留音节数最少的方案（"wo shi" 优先于 "wo shi" 的其它切法），并截断数量
        int minSize = Integer.MAX_VALUE;
        for (List<String> s : all) {
            minSize = Math.min(minSize, s.size());
        }
        for (List<String> s : all) {
            if (s.size() == minSize && result.size() < MAX_SPLITS) {
                result.add(s);
            }
        }
        return result;
    }

    /** 把切分结果拼成词典里的空格分隔形式，如 "wo shi" */
    public static String join(List<String> syllables) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < syllables.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(syllables.get(i));
        }
        return sb.toString();
    }
}
