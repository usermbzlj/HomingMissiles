package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.util.CommandUtil;

import java.util.List;

public final class CommandUtilTest {
    private static void equal(Object actual, Object expected, String name) {
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    public static void main(String[] args) {
        equal(CommandUtil.levenshtein("reload", "reolad"), 2, "levenshtein transposition");
        equal(CommandUtil.closest("reolad", List.of("help", "reload", "status")), "reload", "closest reload");
        equal(CommandUtil.closest("zzzzzz", List.of("help", "reload", "status")), null, "distant no suggestion");
        equal(CommandUtil.filterPrefix(List.of("status", "show", "reload"), "s"),
                List.of("show", "status"), "prefix sorted");
        System.out.println("CommandUtilTest: PASS");
    }
}
