package com.cong.fishisland.game.framework.landlords.util.poker;

import com.cong.fishisland.game.framework.landlords.enums.poker.PokerPatternEnum;
import com.cong.fishisland.game.framework.landlords.enums.poker.PokerValueEnum;
import com.cong.fishisland.game.framework.landlords.model.poker.PatternResult;
import com.cong.fishisland.game.framework.landlords.model.poker.Poker;
import com.cong.fishisland.game.framework.landlords.model.poker.PokerHand;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 扑克牌型匹配器
 *
 * @author cong
 */
public class PokerPatternMatcher {

    private PokerPatternMatcher() {
    }

    /**
     * 分析手牌，返回最佳牌型
     */
    public static PatternResult analyze(PokerHand hand) {
        if (hand == null || hand.isEmpty()) {
            return new PatternResult(PokerPatternEnum.INVALID, 0, 0, new ArrayList<>());
        }

        List<Poker> pokers = hand.getAll();
        int size = pokers.size();

        // 单张
        if (size == 1) {
            return new PatternResult(PokerPatternEnum.SINGLE, pokers.get(0).getLandlordsSortValue(), 1, pokers);
        }

        // 王炸
        if (size == 2 && isJokerBomb(pokers.get(0), pokers.get(1))) {
            return new PatternResult(PokerPatternEnum.JOKER_BOMB, 200, 2, pokers);
        }

        // 按面值分组
        Map<Integer, List<Poker>> valueGroups = pokers.stream()
                .collect(Collectors.groupingBy(Poker::getLandlordsSortValue));

        // 按数量分组
        Map<Integer, List<Integer>> countGroups = valueGroups.entrySet().stream()
                .collect(Collectors.groupingBy(
                        e -> e.getValue().size(),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        // 对每个组内的面值进行排序
        countGroups.values().forEach(values -> 
            values.sort(Collections.reverseOrder())
        );

        // 炸弹、四代二
        if (countGroups.containsKey(4)) {
            PatternResult quadResult = analyzeQuad(countGroups, valueGroups, size);
            if (quadResult != null) {
                return quadResult;
            }
        }

        // 三带一、三带二
        PatternResult tripleResult = analyzeTriple(countGroups, valueGroups, size);
        if (tripleResult != null) {
            return tripleResult;
        }

        // 对子
        PatternResult pairResult = analyzePair(countGroups, valueGroups, size);
        if (pairResult != null) {
            return pairResult;
        }

        // 顺子
        PatternResult straightResult = analyzeStraight(valueGroups, size);
        if (straightResult != null) {
            return straightResult;
        }

        // 连对
        PatternResult doubleStraightResult = analyzeDoubleStraight(countGroups, valueGroups, size);
        if (doubleStraightResult != null) {
            return doubleStraightResult;
        }

        // 飞机
        PatternResult planeResult = analyzePlane(countGroups, valueGroups, size);
        if (planeResult != null) {
            return planeResult;
        }

        return new PatternResult(PokerPatternEnum.INVALID, 0, 0, pokers);
    }

    private static boolean isJokerBomb(Poker p1, Poker p2) {
        return (p1.getValue() == PokerValueEnum.SMALL_JOKER && p2.getValue() == PokerValueEnum.BIG_JOKER) ||
               (p1.getValue() == PokerValueEnum.BIG_JOKER && p2.getValue() == PokerValueEnum.SMALL_JOKER);
    }

    private static PatternResult analyzeQuad(Map<Integer, List<Integer>> countGroups,
                                            Map<Integer, List<Poker>> valueGroups, int size) {
        if (!countGroups.containsKey(4)) {
            return null;
        }

        int quadValue = countGroups.get(4).get(0);
        List<Poker> quadCards = valueGroups.get(quadValue);

        // 四代二：6 张（4 + 2）
        if (size == 6) {
            List<Poker> result = new ArrayList<>(quadCards);

            // 四带一对
            if (countGroups.containsKey(2)) {
                int pairValue = countGroups.get(2).get(0);
                result.addAll(valueGroups.get(pairValue));
                return new PatternResult(PokerPatternEnum.QUAD_PLANE, quadValue, 6, result);
            }

            // 四带两张单牌（不同的单牌）
            int singleCount = 0;
            for (Map.Entry<Integer, List<Poker>> entry : valueGroups.entrySet()) {
                if (entry.getKey() != quadValue && singleCount < 2) {
                    result.add(entry.getValue().get(0));
                    singleCount++;
                }
                if (singleCount == 2) {
                    break;
                }
            }
            if (singleCount == 2) {
                return new PatternResult(PokerPatternEnum.QUAD_PLANE, quadValue, 6, result);
            }
        }

        // 纯炸弹：4 张
        return new PatternResult(PokerPatternEnum.BOMB, quadValue, 4, quadCards);
    }

    private static PatternResult analyzeTriple(Map<Integer, List<Integer>> countGroups,
                                             Map<Integer, List<Poker>> valueGroups, int size) {
        if (!countGroups.containsKey(3)) {
            return null;
        }

        List<Integer> triples = countGroups.get(3);
        int tripleValue = triples.get(0);
        List<Poker> tripleCards = valueGroups.get(tripleValue);

        // 三带二：5 张（3 + 2），且必须存在对子
        if (size == 5 && countGroups.containsKey(2) && !countGroups.get(2).isEmpty()) {
            int pairValue = countGroups.get(2).get(0);
            List<Poker> result = new ArrayList<>(tripleCards);
            result.addAll(valueGroups.get(pairValue));
            return new PatternResult(PokerPatternEnum.TRIPLE_PAIR, tripleValue, 5, result);
        }

        // 三带一：4 张（3 + 1）
        if (size == 4) {
            Optional<List<Poker>> single = valueGroups.entrySet().stream()
                    .filter(e -> e.getKey() != tripleValue)
                    .map(Map.Entry::getValue)
                    .findFirst();

            if (single.isPresent()) {
                List<Poker> result = new ArrayList<>(tripleCards);
                result.add(single.get().get(0));
                return new PatternResult(PokerPatternEnum.TRIPLE_SINGLE, tripleValue, 4, result);
            }
        }

        // 纯三张
        if (size == 3) {
            return new PatternResult(PokerPatternEnum.TRIPLE, tripleValue, 3, tripleCards);
        }

        return null;
    }

    private static PatternResult analyzePair(Map<Integer, List<Integer>> countGroups,
                                          Map<Integer, List<Poker>> valueGroups, int size) {
        if (!countGroups.containsKey(2)) {
            return null;
        }

        // 单独的对子
        if (size == 2) {
            List<Integer> pairs = countGroups.get(2);
            return new PatternResult(PokerPatternEnum.PAIR, pairs.get(0), 2, valueGroups.get(pairs.get(0)));
        }

        return null;
    }

    private static PatternResult analyzeStraight(Map<Integer, List<Poker>> valueGroups, int size) {
        if (size < 5) {
            return null;
        }

        List<Integer> straights = findLongestStraight(new ArrayList<>(valueGroups.keySet()));
        if (straights.isEmpty()) {
            return null;
        }

        List<Poker> result = straights.stream()
                .map(valueGroups::get)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        return new PatternResult(PokerPatternEnum.STRAIGHT, straights.get(0), straights.size(), result);
    }

    private static PatternResult analyzeDoubleStraight(Map<Integer, List<Integer>> countGroups,
                                                      Map<Integer, List<Poker>> valueGroups, int size) {
        if (size < 6 || !countGroups.containsKey(2)) {
            return null;
        }

        // 斗地主规则：连对不能含 2 和王（landlordsSortValue 在 [3,14] 之间）
        List<Integer> pairs = new ArrayList<>(countGroups.get(2));
        pairs.removeIf(v -> v < 3 || v > 14);
        if (pairs.size() < 3) {
            return null;
        }

        // 找出最长的连续对子序列
        List<List<Integer>> consecutiveRuns = findLongestConsecutiveRuns(pairs);
        if (consecutiveRuns.isEmpty()) {
            return null;
        }

        // 取最长的连续序列
        List<Integer> longestRun = consecutiveRuns.get(0);
        int pairCount = longestRun.size();
        if (pairCount < 3) {
            return null;
        }

        // 收集连对的所有牌
        List<Poker> result = new ArrayList<>();
        for (Integer v : longestRun) {
            result.addAll(valueGroups.get(v));
        }

        return new PatternResult(PokerPatternEnum.DOUBLE_STRAIGHT, longestRun.get(0), pairCount * 2, result);
    }

    /**
     * 找出所有连续的对子序列
     * 例如 [8, 7, 6, 5, 4, 3] 返回 [[8, 7, 6, 5, 4, 3]]
     * 例如 [10, 9, 8, 6, 5, 4, 3] 返回 [[10, 9, 8], [6, 5, 4, 3]]
     */
    private static List<List<Integer>> findLongestConsecutiveRuns(List<Integer> sortedDescValues) {
        if (sortedDescValues.isEmpty()) {
            return new ArrayList<>();
        }

        List<List<Integer>> runs = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        current.add(sortedDescValues.get(0));

        for (int i = 1; i < sortedDescValues.size(); i++) {
            int prev = sortedDescValues.get(i - 1);
            int cur = sortedDescValues.get(i);
            if (prev - cur == 1) {
                current.add(cur);
            } else {
                if (current.size() >= 3) {
                    runs.add(new ArrayList<>(current));
                }
                current = new ArrayList<>();
                current.add(cur);
            }
        }
        if (current.size() >= 3) {
            runs.add(new ArrayList<>(current));
        }

        return runs;
    }

    private static PatternResult analyzePlane(Map<Integer, List<Integer>> countGroups,
                                             Map<Integer, List<Poker>> valueGroups, int size) {
        if (!countGroups.containsKey(3) || countGroups.get(3).size() < 2) {
            return null;
        }

        // 斗地主规则：飞机不能含 2 和王（landlordsSortValue 在 [3,14] 之间）
        List<Integer> triples = new ArrayList<>(countGroups.get(3));
        triples.removeIf(v -> v < 3 || v > 14);
        if (triples.size() < 2) {
            return null;
        }

        // 找出最长连续段（triples 已按 reverseOrder 降序）
        List<Integer> longestRun = findLongestConsecutiveRun(triples);
        if (longestRun.size() < 2) {
            return null;
        }
        List<Integer> planeValues = longestRun;
        int planeCardCount = planeValues.size() * 3;

        List<Poker> planeBody = new ArrayList<>();
        for (Integer v : planeValues) {
            planeBody.addAll(valueGroups.get(v));
        }

        // 带单：N 个三张 + N 个单张
        if (size == planeCardCount + planeValues.size()) {
            List<Poker> result = new ArrayList<>(planeBody);
            valueGroups.entrySet().stream()
                    .filter(e -> !planeValues.contains(e.getKey()))
                    .findFirst()
                    .ifPresent(e -> result.add(e.getValue().get(0)));
            return new PatternResult(PokerPatternEnum.PLANE_SINGLE, planeValues.get(0), size, result);
        }

        // 带对：N 个三张 + N 个对子
        if (size == planeCardCount + planeValues.size() * 2
                && countGroups.containsKey(2)
                && countGroups.get(2).size() >= planeValues.size()) {
            List<Integer> pairValues = countGroups.get(2);
            List<Poker> result = new ArrayList<>(planeBody);
            for (int i = 0; i < planeValues.size(); i++) {
                result.addAll(valueGroups.get(pairValues.get(i)));
            }
            return new PatternResult(PokerPatternEnum.PLANE_PAIR, planeValues.get(0), size, result);
        }

        return null;
    }

    /**
     * 从按降序排列的面值列表中找出最长连续递减段
     * 例如 [9, 8, 7, 5, 3] → [9, 8, 7]
     */
    private static List<Integer> findLongestConsecutiveRun(List<Integer> sortedDescValues) {
        if (sortedDescValues.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> longest = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        current.add(sortedDescValues.get(0));

        for (int i = 1; i < sortedDescValues.size(); i++) {
            int prev = sortedDescValues.get(i - 1);
            int cur = sortedDescValues.get(i);
            if (prev - cur == 1) {
                current.add(cur);
            } else {
                if (current.size() > longest.size()) {
                    longest = new ArrayList<>(current);
                }
                current = new ArrayList<>();
                current.add(cur);
            }
        }
        if (current.size() > longest.size()) {
            longest = current;
        }
        return longest;
    }

    /**
     * 验证出牌是否合法
     */
    public static boolean isValidPlay(List<Poker> pokers, PatternResult lastPattern, boolean isFirstPlay) {
        if (pokers == null || pokers.isEmpty()) {
            return false;
        }

        PatternResult currentPattern = analyze(new PokerHand(pokers));

        // 首出牌
        if (isFirstPlay || lastPattern == null) {
            return currentPattern.getPattern() != PokerPatternEnum.INVALID;
        }

        // 王炸可以炸任何牌
        if (currentPattern.getPattern() == PokerPatternEnum.JOKER_BOMB) {
            return true;
        }

        // 炸弹可以炸任何非王炸的牌
        if (currentPattern.getPattern() == PokerPatternEnum.BOMB) {
            return lastPattern.getPattern() != PokerPatternEnum.JOKER_BOMB;
        }

        // 牌型必须相同
        if (!isSamePatternType(currentPattern.getPattern(), lastPattern.getPattern())) {
            return false;
        }

        // 数量必须相同且面值更大
        return currentPattern.getCount() == lastPattern.getCount() &&
               currentPattern.getMainValue() > lastPattern.getMainValue();
    }

    /**
     * 判断两个牌型是否属于同一类型
     */
    private static boolean isSamePatternType(PokerPatternEnum current, PokerPatternEnum last) {
        if (current == PokerPatternEnum.BOMB || current == PokerPatternEnum.JOKER_BOMB ||
            last == PokerPatternEnum.BOMB || last == PokerPatternEnum.JOKER_BOMB) {
            return current == last;
        }

        if ((current == PokerPatternEnum.SINGLE && last == PokerPatternEnum.SINGLE) ||
            (current == PokerPatternEnum.PAIR && last == PokerPatternEnum.PAIR)) {
            return true;
        }

        if (current == PokerPatternEnum.TRIPLE && last == PokerPatternEnum.TRIPLE) {
            return true;
        }

        if (current == PokerPatternEnum.TRIPLE_SINGLE && last == PokerPatternEnum.TRIPLE_SINGLE) {
            return true;
        }

        if (current == PokerPatternEnum.TRIPLE_PAIR && last == PokerPatternEnum.TRIPLE_PAIR) {
            return true;
        }

        if (current == PokerPatternEnum.STRAIGHT && last == PokerPatternEnum.STRAIGHT) {
            return true;
        }

        if (current == PokerPatternEnum.DOUBLE_STRAIGHT && last == PokerPatternEnum.DOUBLE_STRAIGHT) {
            return true;
        }

        if (current == PokerPatternEnum.PLANE && last == PokerPatternEnum.PLANE) {
            return true;
        }

        if (current == PokerPatternEnum.PLANE_SINGLE && last == PokerPatternEnum.PLANE_SINGLE) {
            return true;
        }

        if (current == PokerPatternEnum.PLANE_PAIR && last == PokerPatternEnum.PLANE_PAIR) {
            return true;
        }

        // 四代二只能和四代二比较
        if (current == PokerPatternEnum.QUAD_PLANE && last == PokerPatternEnum.QUAD_PLANE) {
            return true;
        }

        return current == last;
    }

    /**
     * 找出最长的顺子
     */
    private static List<Integer> findLongestStraight(List<Integer> values) {
        if (values.isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> validValues = values.stream()
                .filter(v -> v >= 3 && v <= 14)
                .sorted(Collections.reverseOrder())
                .distinct()
                .collect(Collectors.toList());

        if (validValues.size() < 5) {
            return new ArrayList<>();
        }

        // 使用传统循环避免 lambda 变量赋值问题
        List<Integer> longest = new ArrayList<>();
        List<Integer> current = new ArrayList<>();

        for (int i = 0; i < validValues.size(); i++) {
            if (current.isEmpty() || validValues.get(i - 1) - validValues.get(i) == 1) {
                current.add(validValues.get(i));
            } else {
                current = new ArrayList<>();
                current.add(validValues.get(i));
            }
            if (current.size() > longest.size()) {
                longest = new ArrayList<>(current);
            }
        }

        return longest.size() >= 5 ? longest : new ArrayList<>();
    }
}
