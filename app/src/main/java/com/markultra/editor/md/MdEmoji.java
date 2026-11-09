package com.markultra.editor.md;

import java.util.HashMap;
import java.util.Map;

/**
 * GitHub emoji shortcodes, resolved to their Unicode characters.
 *
 * <p>GitHub accepts {@code :shortcode:} anywhere in text. Shipping the full emoji
 * table would cost a few megabytes, so this covers the codes people actually type in
 * prose and commit messages; unknown codes are left untouched, which is exactly what
 * GitHub does with a code it does not recognise.</p>
 */
public final class MdEmoji {

    private static final Map<String, String> BY_NAME = new HashMap<String, String>(200);

    private MdEmoji() {
    }

    private static void put(String name, String value) {
        BY_NAME.put(name, value);
    }

    static {
        // --- reactions and faces -------------------------------------------------
        put("smile", "\uD83D\uDE04");
        put("smiley", "\uD83D\uDE03");
        put("grin", "\uD83D\uDE01");
        put("grinning", "\uD83D\uDE00");
        put("laughing", "\uD83D\uDE06");
        put("satisfied", "\uD83D\uDE06");
        put("joy", "\uD83D\uDE02");
        put("rofl", "\uD83E\uDD23");
        put("slightly_smiling_face", "\uD83D\uDE42");
        put("blush", "\uD83D\uDE0A");
        put("wink", "\uD83D\uDE09");
        put("heart_eyes", "\uD83D\uDE0D");
        put("kissing_heart", "\uD83D\uDE18");
        put("thinking", "\uD83E\uDD14");
        put("neutral_face", "\uD83D\uDE10");
        put("expressionless", "\uD83D\uDE11");
        put("unamused", "\uD83D\uDE12");
        put("sweat_smile", "\uD83D\uDE05");
        put("sweat", "\uD83D\uDE13");
        put("sob", "\uD83D\uDE2D");
        put("cry", "\uD83D\uDE22");
        put("angry", "\uD83D\uDE20");
        put("rage", "\uD83D\uDE21");
        put("sunglasses", "\uD83D\uDE0E");
        put("nerd_face", "\uD83E\uDD13");
        put("sleeping", "\uD83D\uDE34");
        put("zany_face", "\uD83E\uDD2A");
        put("scream", "\uD83D\uDE31");
        put("flushed", "\uD83D\uDE33");
        put("worried", "\uD83D\uDE1F");
        put("hugs", "\uD83E\uDD17");
        put("partying_face", "\uD83E\uDD73");
        put("smirk", "\uD83D\uDE0F");
        put("upside_down_face", "\uD83D\uDE43");

        // --- gestures and people -------------------------------------------------
        put("thumbsup", "\uD83D\uDC4D");
        put("+1", "\uD83D\uDC4D");
        put("thumbsdown", "\uD83D\uDC4E");
        put("-1", "\uD83D\uDC4E");
        put("ok_hand", "\uD83D\uDC4C");
        put("clap", "\uD83D\uDC4F");
        put("wave", "\uD83D\uDC4B");
        put("pray", "\uD83D\uDE4F");
        put("muscle", "\uD83D\uDCAA");
        put("point_right", "\uD83D\uDC49");
        put("point_left", "\uD83D\uDC48");
        put("point_up", "\u261D");
        put("point_down", "\uD83D\uDC47");
        put("raised_hands", "\uD83D\uDE4C");
        put("eyes", "\uD83D\uDC40");
        put("brain", "\uD83E\uDDE0");
        put("handshake", "\uD83E\uDD1D");
        put("v", "\u270C");

        // --- symbols and status --------------------------------------------------
        put("white_check_mark", "\u2705");
        put("heavy_check_mark", "\u2714");
        put("x", "\u274C");
        put("negative_squared_cross_mark", "\u274E");
        put("warning", "\u26A0");
        put("no_entry", "\u26D4");
        put("no_entry_sign", "\uD83D\uDEAB");
        put("bangbang", "\u203C");
        put("interrobang", "\u2049");
        put("question", "\u2753");
        put("exclamation", "\u2757");
        put("100", "\uD83D\uDCAF");
        put("heavy_plus_sign", "\u2795");
        put("heavy_minus_sign", "\u2796");
        put("infinity", "\u267E");
        put("recycle", "\u267B");
        put("link", "\uD83D\uDD17");
        put("lock", "\uD83D\uDD12");
        put("unlock", "\uD83D\uDD13");
        put("key", "\uD83D\uDD11");
        put("mag", "\uD83D\uDD0D");
        put("bell", "\uD83D\uDD14");
        put("mute", "\uD83D\uDD15");
        put("pushpin", "\uD83D\uDCCC");
        put("paperclip", "\uD83D\uDCCE");
        put("clipboard", "\uD83D\uDCCB");
        put("bookmark", "\uD83D\uDD16");

        // --- objects, tech, work -------------------------------------------------
        put("rocket", "\uD83D\uDE80");
        put("star", "\u2B50");
        put("star2", "\uD83C\uDF1F");
        put("sparkles", "\u2728");
        put("zap", "\u26A1");
        put("fire", "\uD83D\uDD25");
        put("boom", "\uD83D\uDCA5");
        put("collision", "\uD83D\uDCA5");
        put("bug", "\uD83D\uDC1B");
        put("beetle", "\uD83D\uDC1E");
        put("hammer", "\uD83D\uDD28");
        put("wrench", "\uD83D\uDD27");
        put("nut_and_bolt", "\uD83D\uDD29");
        put("gear", "\u2699");
        put("package", "\uD83D\uDCE6");
        put("truck", "\uD83D\uDE9A");
        put("computer", "\uD83D\uDCBB");
        put("keyboard", "\u2328");
        put("iphone", "\uD83D\uDCF1");
        put("phone", "\u260E");
        put("email", "\u2709");
        put("envelope", "\u2709");
        put("memo", "\uD83D\uDCDD");
        put("pencil2", "\u270F");
        put("books", "\uD83D\uDCDA");
        put("book", "\uD83D\uDCD6");
        put("blue_book", "\uD83D\uDCD8");
        put("ledger", "\uD83D\uDCD2");
        put("scroll", "\uD83D\uDCDC");
        put("page_facing_up", "\uD83D\uDCC4");
        put("chart_with_upwards_trend", "\uD83D\uDCC8");
        put("bar_chart", "\uD83D\uDCCA");
        put("calendar", "\uD83D\uDCC5");
        put("date", "\uD83D\uDCC5");
        put("clock1", "\uD83D\uDD50");
        put("hourglass", "\u231B");
        put("stopwatch", "\u23F1");
        put("alarm_clock", "\u23F0");
        put("art", "\uD83C\uDFA8");
        put("camera", "\uD83D\uDCF7");
        put("movie_camera", "\uD83C\uDFA5");
        put("musical_note", "\uD83C\uDFB5");
        put("headphones", "\uD83C\uDFA7");
        put("microphone", "\uD83C\uDFA4");
        put("loudspeaker", "\uD83D\uDCE2");
        put("megaphone", "\uD83D\uDCE3");

        // --- nature, food, travel ------------------------------------------------
        put("sunny", "\u2600");
        put("cloud", "\u2601");
        put("umbrella", "\u2614");
        put("snowflake", "\u2744");
        put("zap_cloud", "\u26C8");
        put("earth_africa", "\uD83C\uDF0D");
        put("earth_americas", "\uD83C\uDF0E");
        put("earth_asia", "\uD83C\uDF0F");
        put("globe_with_meridians", "\uD83C\uDF10");
        put("moon", "\uD83C\uDF14");
        put("seedling", "\uD83C\uDF31");
        put("leaves", "\uD83C\uDF43");
        put("maple_leaf", "\uD83C\uDF41");
        put("cactus", "\uD83C\uDF35");
        put("tada", "\uD83C\uDF89");
        put("gift", "\uD83C\uDF81");
        put("trophy", "\uD83C\uDFC6");
        put("medal_sports", "\uD83C\uDFC5");
        put("dart", "\uD83C\uDFAF");
        put("coffee", "\u2615");
        put("beer", "\uD83C\uDF7A");
        put("beers", "\uD83C\uDF7B");
        put("pizza", "\uD83C\uDF55");
        put("hamburger", "\uD83C\uDF54");
        put("apple", "\uD83C\uDF4E");
        put("cake", "\uD83C\uDF70");
        put("birthday", "\uD83C\uDF82");
        put("house", "\uD83C\uDFE0");
        put("office", "\uD83C\uDFE2");
        put("airplane", "\u2708");
        put("car", "\uD83D\uDE97");
        put("bike", "\uD83D\uDEB2");

        // --- hearts and misc -----------------------------------------------------
        put("heart", "\u2764");
        put("orange_heart", "\uD83E\uDDE1");
        put("yellow_heart", "\uD83D\uDC9B");
        put("green_heart", "\uD83D\uDC9A");
        put("blue_heart", "\uD83D\uDC99");
        put("purple_heart", "\uD83D\uDC9C");
        put("black_heart", "\uD83D\uDDA4");
        put("broken_heart", "\uD83D\uDC94");
        put("sparkling_heart", "\uD83D\uDC96");
        put("two_hearts", "\uD83D\uDC95");
        put("skull", "\uD83D\uDC80");
        put("ghost", "\uD83D\uDC7B");
        put("alien", "\uD83D\uDC7D");
        put("robot", "\uD83E\uDD16");
        put("santa", "\uD83C\uDF85");
        put("guitar", "\uD83C\uDFB8");
        put("video_game", "\uD83C\uDFAE");
        put("checkered_flag", "\uD83C\uDFC1");
        put("triangular_flag_on_post", "\uD83D\uDEA9");
        put("construction", "\uD83D\uDEA7");
        put("rotating_light", "\uD83D\uDEA8");
        put("speech_balloon", "\uD83D\uDCAC");
        put("thought_balloon", "\uD83D\uDCAD");
        put("information_source", "\u2139");
        put("heavy_exclamation_mark", "\u2757");
        put("arrow_right", "\u27A1");
        put("arrow_left", "\u2B05");
        put("arrow_up", "\u2B06");
        put("arrow_down", "\u2B07");
        put("arrows_counterclockwise", "\uD83D\uDD04");
        put("repeat", "\uD83D\uDD01");
        put("fast_forward", "\u23E9");
        put("hourglass_flowing_sand", "\u23F3");
    }

    /** @return the emoji for a shortcode name, or null when unknown. */
    public static String lookup(String name) {
        if (name == null) return null;
        return BY_NAME.get(name);
    }

    public static boolean isKnown(String name) {
        return name != null && BY_NAME.containsKey(name);
    }

    /** Number of shortcodes in the table, used by the tests and the about dialog. */
    public static int size() {
        return BY_NAME.size();
    }
}
