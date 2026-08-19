package com.cong.fishisland.game.framework.landlords.service;

import com.cong.fishisland.game.common.model.room.GameRoom;
import com.cong.fishisland.game.framework.landlords.enums.poker.PokerPatternEnum;
import com.cong.fishisland.game.framework.landlords.enums.poker.PokerValueEnum;
import com.cong.fishisland.game.framework.landlords.model.LandlordsPlayer;
import com.cong.fishisland.game.framework.landlords.model.LandlordsRoom;
import com.cong.fishisland.game.framework.landlords.model.poker.Poker;
import com.cong.fishisland.game.framework.landlords.model.poker.PokerHand;
import com.cong.fishisland.game.framework.landlords.model.poker.PatternResult;
import com.cong.fishisland.game.framework.landlords.util.poker.PokerPatternMatcher;
import com.cong.fishisland.game.framework.landlords.util.poker.PokerSorter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 斗地主AI托管服务
 * 负责处理AI托管时的自动操作
 *
 * @author cong
 */
@Slf4j
@Service
public class LandlordsRobotService {

    /**
     * AI叫分策略：始终不叫
     */
    public int getRobScore() {
        return 0;
    }

    /**
     * AI出牌策略
     */
    public List<String> getPlayCards(GameRoom room, Long playerId) {
        if (!(room instanceof LandlordsRoom)) {
            return Collections.emptyList();
        }
        LandlordsRoom landlordsRoom = (LandlordsRoom) room;
        LandlordsPlayer player = landlordsRoom.getPlayer(playerId) instanceof LandlordsPlayer
                ? (LandlordsPlayer) landlordsRoom.getPlayer(playerId)
                : null;
        if (player == null || player.getHand() == null) {
            return Collections.emptyList();
        }

        PokerHand hand = player.getHand();
        PokerHand lastPlayedCards = landlordsRoom.getLastPlayedCards();
        boolean isFirstPlay = landlordsRoom.getLastPlayerId() == null || landlordsRoom.getLastPlayerId().equals(playerId);

        PokerHand sortedHand = new PokerHand(hand.getAll());
        PokerSorter.sortByLandlordsWithUniversal(sortedHand);

        if (isFirstPlay || lastPlayedCards == null || lastPlayedCards.isEmpty()) {
            return playSmallestSingle(sortedHand);
        } else {
            PatternResult lastPattern = PokerPatternMatcher.analyze(lastPlayedCards);
            return playToBeat(sortedHand, lastPattern);
        }
    }

    private List<String> playSmallestSingle(PokerHand hand) {
        if (hand.isEmpty()) {
            return Collections.emptyList();
        }

        List<Poker> sortedList = hand.getAll().stream()
                .sorted(Comparator.comparingInt(Poker::getLandlordsSortValue))
                .collect(Collectors.toList());
        Poker smallest = sortedList.get(0);

        return Collections.singletonList(smallest.getId());
    }

    private List<String> playToBeat(PokerHand hand, PatternResult lastPattern) {
        if (hand.isEmpty()) {
            return Collections.emptyList();
        }

        PokerPatternEnum patternType = lastPattern.getPattern();
        List<Poker> handList = hand.getAll();
        int lastValue = lastPattern.getMainValue();

        switch (patternType) {
            case SINGLE:
                return playSingleToBeat(handList, lastValue);

            case PAIR:
                return playPairToBeat(handList, lastValue);

            case TRIPLE:
                return playTripleToBeat(handList, lastValue);

            case TRIPLE_SINGLE:
                return playTripleWithSingleToBeat(hand, lastValue);

            case TRIPLE_PAIR:
                return playTripleWithPairToBeat(hand, lastValue);

            case STRAIGHT:
                return playStraightToBeat(handList, lastValue);

            case DOUBLE_STRAIGHT:
                return playStraightPairToBeat(handList, lastValue);

            case PLANE:
            case PLANE_SINGLE:
            case PLANE_PAIR:
                return playPlaneToBeat(handList, lastValue, lastPattern.getCount());

            case BOMB:
                return playBombToBeat(handList, lastValue);

            case QUAD_PLANE:
                return playQuadPlaneToBeat(hand, lastValue);

            case JOKER_BOMB:
                return Collections.emptyList();

            default:
                return playSmallestSingle(new PokerHand(handList));
        }
    }

    private List<String> playSingleToBeat(List<Poker> hand, int lastValue) {
        for (Poker poker : hand) {
            if (poker.getLandlordsSortValue() > lastValue) {
                return Collections.singletonList(poker.getId());
            }
        }
        return playBombToBeat(hand, lastValue);
    }

    private List<String> playPairToBeat(List<Poker> hand, int lastValue) {
        List<Poker> pairs = findPairs(hand);
        for (Poker pair : pairs) {
            if (pair.getLandlordsSortValue() > lastValue) {
                String pairId = findPairId(hand, pair);
                if (pairId != null) {
                    return Arrays.asList(pair.getId(), pairId);
                }
            }
        }
        return playBombToBeat(hand, lastValue);
    }

    private List<String> playTripleToBeat(List<Poker> hand, int lastValue) {
        List<Poker> triples = findTriples(hand);
        for (Poker triple : triples) {
            if (triple.getLandlordsSortValue() > lastValue) {
                List<String> result = new ArrayList<>();
                result.add(triple.getId());
                for (Poker p : hand) {
                    if (p != triple && p.getValue() == triple.getValue()) {
                        result.add(p.getId());
                        break;
                    }
                }
                return result;
            }
        }
        return playBombToBeat(hand, lastValue);
    }

    private List<String> playTripleWithSingleToBeat(PokerHand hand, int lastValue) {
        List<Poker> triples = findTriples(hand.getAll());
        if (triples.isEmpty()) {
            return playBombToBeat(hand.getAll(), lastValue);
        }

        for (Poker triple : triples) {
            if (triple.getLandlordsSortValue() > lastValue) {
                List<String> result = new ArrayList<>();
                result.add(triple.getId());
                int count = 1;
                for (Poker p : hand.getAll()) {
                    if (p != triple && p.getValue() == triple.getValue() && count < 2) {
                        result.add(p.getId());
                        count++;
                    }
                }
                for (Poker p : hand.getAll()) {
                    if (p.getValue() != triple.getValue()) {
                        result.add(p.getId());
                        break;
                    }
                }
                return result;
            }
        }
        return playBombToBeat(hand.getAll(), lastValue);
    }

    private List<String> playTripleWithPairToBeat(PokerHand hand, int lastValue) {
        List<Poker> triples = findTriples(hand.getAll());
        List<List<Poker>> pairs = findAllPairs(hand.getAll());
        if (triples.isEmpty() || pairs.isEmpty()) {
            return playBombToBeat(hand.getAll(), lastValue);
        }

        for (Poker triple : triples) {
            if (triple.getValue().getValue() > lastValue) {
                Set<PokerValueEnum> tripleValues = new HashSet<>();
                tripleValues.add(triple.getValue());
                for (Poker p : hand.getAll()) {
                    if (p.getValue() == triple.getValue()) {
                        tripleValues.add(p.getValue());
                    }
                }

                List<Poker> smallestPair = null;
                for (List<Poker> pair : pairs) {
                    if (!tripleValues.contains(pair.get(0).getValue())) {
                        smallestPair = pair;
                        break;
                    }
                }

                if (smallestPair == null) {
                    return playBombToBeat(hand.getAll(), lastValue);
                }

                List<String> result = new ArrayList<>();
                result.add(triple.getId());
                int count = 1;
                for (Poker p : hand.getAll()) {
                    if (p != triple && p.getValue() == triple.getValue() && count < 2) {
                        result.add(p.getId());
                        count++;
                    }
                }
                result.addAll(smallestPair.stream().map(Poker::getId).collect(Collectors.toList()));
                return result;
            }
        }
        return playBombToBeat(hand.getAll(), lastValue);
    }

    private List<String> playStraightToBeat(List<Poker> hand, int lastValue) {
        List<List<Poker>> straights = findStraights(hand);
        for (List<Poker> straight : straights) {
            int straightMax = straight.stream()
                    .mapToInt(p -> p.getLandlordsSortValue())
                    .max().orElse(0);
            if (straightMax > lastValue) {
                return straight.stream().map(Poker::getId).collect(Collectors.toList());
            }
        }
        return playBombToBeat(hand, lastValue);
    }

    private List<String> playStraightPairToBeat(List<Poker> hand, int lastValue) {
        List<List<Poker>> doubleStraights = findDoubleStraights(hand);
        for (List<Poker> doubleStraight : doubleStraights) {
            int dsMax = doubleStraight.stream()
                    .mapToInt(p -> p.getLandlordsSortValue())
                    .max().orElse(0);
            if (dsMax > lastValue) {
                return doubleStraight.stream().map(Poker::getId).collect(Collectors.toList());
            }
        }
        return playBombToBeat(hand, lastValue);
    }

    private List<String> playPlaneToBeat(List<Poker> hand, int lastValue, int cardCount) {
        List<List<Poker>> planes = findPlanes(hand);
        for (List<Poker> plane : planes) {
            int planeMax = plane.stream()
                    .mapToInt(p -> p.getLandlordsSortValue())
                    .max().orElse(0);
            int planeCardCount = plane.size();

            int extraCardsNeeded = cardCount - planeCardCount;
            if (extraCardsNeeded <= 0) {
                if (planeMax > lastValue) {
                    return plane.stream().map(Poker::getId).collect(Collectors.toList());
                }
            } else {
                List<Poker> extras = findExtraCards(hand, plane, extraCardsNeeded);
                if (!extras.isEmpty()) {
                    List<String> result = new ArrayList<>(plane.stream().map(Poker::getId).collect(Collectors.toList()));
                    result.addAll(extras.stream().map(Poker::getId).collect(Collectors.toList()));
                    if (planeMax > lastValue) {
                        return result;
                    }
                }
            }
        }
        return playBombToBeat(hand, lastValue);
    }

    private List<List<Poker>> findStraights(List<Poker> hand) {
        List<List<Poker>> straights = new ArrayList<>();

        Map<Integer, List<Poker>> grouped = hand.stream()
                .collect(Collectors.groupingBy(Poker::getLandlordsSortValue));

        Set<Integer> validValues = grouped.keySet().stream()
                .filter(v -> v >= 3 && v <= 14)
                .collect(Collectors.toSet());

        if (validValues.size() < 5) {
            return straights;
        }

        List<Integer> sortedValues = validValues.stream()
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());

        List<List<Integer>> runs = findConsecutiveRuns(sortedValues);

        for (List<Integer> run : runs) {
            if (run.size() >= 5) {
                List<Poker> longestStraight = new ArrayList<>();
                for (Integer v : run) {
                    for (Poker p : grouped.get(v)) {
                        if (longestStraight.size() < run.size()) {
                            longestStraight.add(p);
                        }
                        if (longestStraight.size() == run.size()) {
                            break;
                        }
                    }
                }
                if (!longestStraight.isEmpty()) {
                    straights.add(longestStraight);
                }
            }
        }

        straights.sort((a, b) -> {
            int maxA = a.stream().mapToInt(p -> p.getLandlordsSortValue()).max().orElse(0);
            int maxB = b.stream().mapToInt(p -> p.getLandlordsSortValue()).max().orElse(0);
            return Integer.compare(maxA, maxB);
        });

        return straights;
    }

    private List<List<Integer>> findConsecutiveRuns(List<Integer> sortedValues) {
        List<List<Integer>> runs = new ArrayList<>();
        if (sortedValues.isEmpty()) {
            return runs;
        }

        List<Integer> current = new ArrayList<>();
        current.add(sortedValues.get(0));

        for (int i = 1; i < sortedValues.size(); i++) {
            if (sortedValues.get(i - 1) - sortedValues.get(i) == 1) {
                current.add(sortedValues.get(i));
            } else {
                if (current.size() >= 2) {
                    runs.add(new ArrayList<>(current));
                }
                current = new ArrayList<>();
                current.add(sortedValues.get(i));
            }
        }
        if (current.size() >= 2) {
            runs.add(current);
        }

        return runs;
    }

    private List<List<Poker>> findDoubleStraights(List<Poker> hand) {
        List<List<Poker>> doubleStraights = new ArrayList<>();

        Map<Integer, List<Poker>> grouped = hand.stream()
                .collect(Collectors.groupingBy(Poker::getLandlordsSortValue));

        List<Integer> pairValues = new ArrayList<>();
        for (Map.Entry<Integer, List<Poker>> entry : grouped.entrySet()) {
            if (entry.getValue().size() >= 2 && entry.getKey() >= 3 && entry.getKey() <= 14) {
                pairValues.add(entry.getKey());
            }
        }

        if (pairValues.size() < 3) {
            return doubleStraights;
        }

        pairValues.sort(Collections.reverseOrder());
        List<List<Integer>> runs = findConsecutiveRuns(pairValues);

        for (List<Integer> run : runs) {
            if (run.size() >= 3) {
                List<Poker> ds = new ArrayList<>();
                for (Integer v : run) {
                    List<Poker> pair = grouped.get(v);
                    ds.add(pair.get(0));
                    ds.add(pair.get(1));
                }
                doubleStraights.add(ds);
            }
        }

        doubleStraights.sort((a, b) -> {
            int maxA = a.stream().mapToInt(p -> p.getLandlordsSortValue()).max().orElse(0);
            int maxB = b.stream().mapToInt(p -> p.getLandlordsSortValue()).max().orElse(0);
            return Integer.compare(maxA, maxB);
        });

        return doubleStraights;
    }

    private List<List<Poker>> findPlanes(List<Poker> hand) {
        List<List<Poker>> planes = new ArrayList<>();

        Map<Integer, List<Poker>> grouped = hand.stream()
                .collect(Collectors.groupingBy(Poker::getLandlordsSortValue));

        List<Integer> tripleValues = new ArrayList<>();
        for (Map.Entry<Integer, List<Poker>> entry : grouped.entrySet()) {
            if (entry.getValue().size() >= 3 && entry.getKey() >= 3 && entry.getKey() <= 14) {
                tripleValues.add(entry.getKey());
            }
        }

        if (tripleValues.size() < 2) {
            return planes;
        }

        tripleValues.sort(Collections.reverseOrder());
        List<List<Integer>> runs = findConsecutiveRuns(tripleValues);

        for (List<Integer> run : runs) {
            if (run.size() >= 2) {
                List<Poker> plane = new ArrayList<>();
                for (Integer v : run) {
                    plane.addAll(grouped.get(v));
                }
                planes.add(plane);
            }
        }

        return planes;
    }

    private List<Poker> findExtraCards(List<Poker> hand, List<Poker> plane, int count) {
        Set<Integer> planeValues = plane.stream()
                .map(Poker::getLandlordsSortValue)
                .collect(Collectors.toSet());

        return hand.stream()
                .filter(p -> !planeValues.contains(p.getLandlordsSortValue()))
                .sorted(Comparator.comparingInt(p -> p.getLandlordsSortValue()))
                .limit(count)
                .collect(Collectors.toList());
    }

    private List<String> playQuadPlaneToBeat(PokerHand hand, int lastValue) {
        List<List<Poker>> quads = findQuads(hand.getAll());
        for (List<Poker> quad : quads) {
            if (quad.get(0).getLandlordsSortValue() > lastValue) {
                List<String> result = quad.stream().map(Poker::getId).collect(Collectors.toList());

                // 尝试找一对
                List<List<Poker>> pairs = findAllPairs(hand.getAll());
                for (List<Poker> pair : pairs) {
                    if (!pair.get(0).getValue().equals(quad.get(0).getValue())) {
                        result.addAll(pair.stream().map(Poker::getId).collect(Collectors.toList()));
                        return result;
                    }
                }

                // 找两张单牌
                Set<PokerValueEnum> quadValues = quad.stream()
                        .map(Poker::getValue)
                        .collect(Collectors.toSet());
                int singleCount = 0;
                for (Poker p : hand.getAll()) {
                    if (!quadValues.contains(p.getValue()) && singleCount < 2) {
                        result.add(p.getId());
                        singleCount++;
                    }
                    if (singleCount == 2) {
                        return result;
                    }
                }
            }
        }
        return playBombToBeat(hand.getAll(), lastValue);
    }

    private List<String> playBombToBeat(List<Poker> hand, int lastValue) {
        List<List<Poker>> bombs = findBombs(hand);
        for (List<Poker> bomb : bombs) {
            if (bomb.get(0).getValue().getValue() > lastValue) {
                return bomb.stream().map(Poker::getId).collect(Collectors.toList());
            }
        }

        if (hasRocket(hand)) {
            return getRocketIds(hand);
        }

        return Collections.emptyList();
    }

    private List<Poker> findPairs(List<Poker> hand) {
        List<Poker> pairs = new ArrayList<>();
        Set<PokerValueEnum> usedValues = new HashSet<>();

        for (int i = 0; i < hand.size(); i++) {
            Poker current = hand.get(i);
            if (usedValues.contains(current.getValue())) {
                continue;
            }
            for (int j = i + 1; j < hand.size(); j++) {
                if (current.getValue() == hand.get(j).getValue()) {
                    pairs.add(hand.get(j));
                    usedValues.add(current.getValue());
                    break;
                }
            }
        }
        pairs.sort(Comparator.comparingInt(p -> p.getValue().getValue()));
        return pairs;
    }

    private List<List<Poker>> findAllPairs(List<Poker> hand) {
        List<List<Poker>> pairs = new ArrayList<>();
        List<Poker> used = new ArrayList<>();

        for (int i = 0; i < hand.size(); i++) {
            if (used.contains(hand.get(i))) continue;

            for (int j = i + 1; j < hand.size(); j++) {
                if (used.contains(hand.get(j))) continue;

                if (hand.get(i).getValue() == hand.get(j).getValue()) {
                    List<Poker> pair = Arrays.asList(hand.get(i), hand.get(j));
                    pairs.add(pair);
                    used.add(hand.get(i));
                    used.add(hand.get(j));
                    break;
                }
            }
        }

        pairs.sort(Comparator.comparingInt(p -> p.get(0).getValue().getValue()));
        return pairs;
    }

    private List<Poker> findTriples(List<Poker> hand) {
        List<Poker> triples = new ArrayList<>();
        Set<PokerValueEnum> usedValues = new HashSet<>();

        for (int i = 0; i < hand.size(); i++) {
            Poker current = hand.get(i);
            if (usedValues.contains(current.getValue())) {
                continue;
            }
            int count = 1;
            for (int j = i + 1; j < hand.size(); j++) {
                if (current.getValue() == hand.get(j).getValue()) {
                    count++;
                }
            }
            if (count >= 3) {
                triples.add(current);
                usedValues.add(current.getValue());
            }
        }
        triples.sort(Comparator.comparingInt(p -> p.getValue().getValue()));
        return triples;
    }

    private List<List<Poker>> findBombs(List<Poker> hand) {
        List<List<Poker>> bombs = new ArrayList<>();
        Map<PokerValueEnum, List<Poker>> grouped = new HashMap<>();

        for (Poker p : hand) {
            grouped.computeIfAbsent(p.getValue(), k -> new ArrayList<>()).add(p);
        }

        for (List<Poker> group : grouped.values()) {
            if (group.size() >= 4) {
                bombs.add(group);
            }
        }

        bombs.sort(Comparator.comparingInt(b -> b.get(0).getValue().getValue()));
        return bombs;
    }

    private List<List<Poker>> findQuads(List<Poker> hand) {
        List<List<Poker>> quads = new ArrayList<>();
        Map<PokerValueEnum, List<Poker>> grouped = new HashMap<>();

        for (Poker p : hand) {
            grouped.computeIfAbsent(p.getValue(), k -> new ArrayList<>()).add(p);
        }

        for (List<Poker> group : grouped.values()) {
            if (group.size() >= 4) {
                quads.add(group);
            }
        }

        quads.sort(Comparator.comparingInt(q -> q.get(0).getValue().getValue()));
        return quads;
    }

    private String findPairId(List<Poker> hand, Poker pair) {
        for (Poker p : hand) {
            if (!p.equals(pair) && p.getValue() == pair.getValue()) {
                return p.getId();
            }
        }
        return null;
    }

    private boolean hasRocket(List<Poker> hand) {
        boolean hasSmallJoker = false;
        boolean hasBigJoker = false;

        for (Poker p : hand) {
            int value = p.getValue().getValue();
            if (value == 16) {
                hasSmallJoker = true;
            } else if (value == 17) {
                hasBigJoker = true;
            }
        }

        return hasSmallJoker && hasBigJoker;
    }

    private List<String> getRocketIds(List<Poker> hand) {
        List<String> rocket = new ArrayList<>();
        for (Poker p : hand) {
            int value = p.getValue().getValue();
            if (value == 16 || value == 17) {
                rocket.add(p.getId());
            }
        }
        return rocket;
    }
}
