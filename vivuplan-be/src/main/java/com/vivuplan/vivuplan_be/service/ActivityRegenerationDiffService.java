package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.ItineraryDay;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ActivityRegenerationDiffService {

    private static final double FUZZY_MATCH_THRESHOLD = 0.55;
    private static final double AMBIGUITY_MARGIN = 0.08;
    private static final double SCORE_EPSILON = 0.000_001;
    private static final List<String> COMPARABLE_FIELDS = List.of(
            "TIME", "NAME", "TYPE", "LOCATION", "DURATION", "COST", "NOTE");
    private static final Set<String> EXPERIENCE_TYPES = Set.of(
            "food", "cafe", "attraction", "activity", "nightlife");
    private final ActivityMetadataReconciliationService metadataReconciliationService;

    public ActivityRegenerationDiffService(
            ActivityMetadataReconciliationService metadataReconciliationService) {
        this.metadataReconciliationService = metadataReconciliationService;
    }

    public DiffResult diff(
            ItineraryDay oldDay,
            TripDto.DayResponse proposedDay,
            String changeNamespace) {
        return diff(oldDay, proposedDay, changeNamespace, Map.of());
    }

    public DiffResult diff(
            ItineraryDay oldDay,
            TripDto.DayResponse proposedDay,
            String changeNamespace,
            Map<Integer, Integer> sourceOldIndexByNewIndex) {
        if (changeNamespace == null || changeNamespace.isBlank()) {
            throw new IllegalArgumentException("Change namespace is required");
        }
        List<TripDto.ActivityResponse> oldActivities = toResponses(oldDay);
        List<TripDto.ActivityResponse> newActivities = proposedDay.getActivities() == null
                ? List.of()
                : proposedDay.getActivities();
        MatchResult matchResult = matchActivities(
                oldActivities,
                newActivities,
                sourceOldIndexByNewIndex == null ? Map.of() : sourceOldIndexByNewIndex);
        List<Match> matches = matchResult.matches();

        Set<Integer> matchedOld = matches.stream().map(Match::oldIndex).collect(Collectors.toSet());
        Set<Integer> matchedNew = matches.stream().map(Match::newIndex).collect(Collectors.toSet());
        List<PendingChange> pendingChanges = new ArrayList<>();
        List<TripDto.RegenerateUnchangedActivity> unchangedActivities = new ArrayList<>();
        List<ActivityMetadataReconciliationService.MetadataPatch> metadataPatches = new ArrayList<>();

        for (Match match : matches) {
            TripDto.ActivityResponse oldActivity = oldActivities.get(match.oldIndex());
            TripDto.ActivityResponse newActivity = newActivities.get(match.newIndex());
            List<String> changedFields = changedFields(oldActivity, newActivity);
            if (!changedFields.isEmpty()) {
                pendingChanges.add(new PendingChange(
                        "MODIFIED",
                        oldActivity,
                        newActivity,
                        changedFields,
                        match.oldIndex(),
                        match.newIndex()));
                continue;
            }

            ActivityMetadataReconciliationService.MetadataPatch metadataPatch =
                    metadataReconciliationService
                            .buildPatch(oldActivity, newActivity, match.oldIndex())
                            .orElse(null);
            TripDto.RegenerateUnchangedActivity unchanged = new TripDto.RegenerateUnchangedActivity();
            unchanged.setActivity(copy(oldActivity));
            unchanged.setMetadataUpgradeAvailable(metadataPatch != null);
            unchangedActivities.add(unchanged);
            if (metadataPatch != null) {
                metadataPatches.add(metadataPatch);
            }
        }

        for (int index = 0; index < oldActivities.size(); index++) {
            if (!matchedOld.contains(index)) {
                pendingChanges.add(new PendingChange(
                        "REMOVED",
                        oldActivities.get(index),
                        null,
                        List.of(),
                        index,
                        null));
            }
        }
        for (int index = 0; index < newActivities.size(); index++) {
            if (!matchedNew.contains(index)) {
                pendingChanges.add(new PendingChange(
                        "ADDED",
                        null,
                        newActivities.get(index),
                        List.of(),
                        null,
                        index));
            }
        }

        pendingChanges.sort(Comparator
                .comparing(PendingChange::displayTime, Comparator.nullsLast(String::compareTo))
                .thenComparing(change -> change.oldIndex() != null ? change.oldIndex() : Integer.MAX_VALUE)
                .thenComparing(change -> change.newIndex() != null ? change.newIndex() : Integer.MAX_VALUE));
        unchangedActivities.sort(Comparator.comparingInt(activity ->
                activity.getActivity() != null ? activity.getActivity().getSortOrder() : Integer.MAX_VALUE));

        List<TripDto.RegenerateActivityChange> changes = new ArrayList<>();
        for (int index = 0; index < pendingChanges.size(); index++) {
            PendingChange pending = pendingChanges.get(index);
            TripDto.RegenerateActivityChange change = new TripDto.RegenerateActivityChange();
            change.setChangeId(changeNamespace + ":change-" + (index + 1));
            change.setType(pending.type());
            change.setOldActivity(copy(pending.oldActivity()));
            change.setNewActivity(copy(pending.newActivity()));
            change.setChangedFields(pending.changedFields());
            change.setOldIndex(pending.oldIndex());
            change.setNewIndex(pending.newIndex());
            changes.add(change);
        }

        return new DiffResult(
                changes,
                unchangedActivities,
                metadataPatches,
                fingerprint(oldDay),
                new MatchingDiagnostics(
                        matchResult.referenceMatches(),
                        matchResult.exactMatches(),
                        matchResult.semanticMatches(),
                        matchResult.ambiguousPairs(),
                        (int) pendingChanges.stream().filter(change -> "ADDED".equals(change.type())).count(),
                        (int) pendingChanges.stream().filter(change -> "REMOVED".equals(change.type())).count()));
    }

    public TripDto.DayResponse merge(
            ItineraryDay oldDay,
            TripDto.DayResponse proposedDay,
            List<TripDto.RegenerateActivityChange> changes,
            Set<String> selectedChangeIds,
            List<ActivityMetadataReconciliationService.MetadataPatch> metadataPatches,
            boolean applyMetadataUpgrades) {
        Set<String> allChangeIds = changes.stream()
                .map(TripDto.RegenerateActivityChange::getChangeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean applyAllActionable = !allChangeIds.isEmpty() && selectedChangeIds.equals(allChangeIds);

        List<TripDto.ActivityResponse> merged = toResponses(oldDay).stream()
                .map(this::copy)
                .collect(Collectors.toCollection(ArrayList::new));

        List<TripDto.RegenerateActivityChange> selectedChanges = changes.stream()
                .filter(change -> selectedChangeIds.contains(change.getChangeId()))
                .toList();

        selectedChanges.stream()
                .filter(change -> "REMOVED".equals(change.getType()))
                .sorted(Comparator.comparing(TripDto.RegenerateActivityChange::getOldIndex).reversed())
                .forEach(change -> removeMatchedOldActivity(merged, change));

        selectedChanges.stream()
                .filter(change -> "MODIFIED".equals(change.getType()))
                .forEach(change -> replaceMatchedOldActivity(merged, change));

        selectedChanges.stream()
                .filter(change -> "ADDED".equals(change.getType()))
                .forEach(change -> merged.add(copy(change.getNewActivity())));

        if (applyMetadataUpgrades) {
            for (ActivityMetadataReconciliationService.MetadataPatch patch : metadataPatches) {
                int position = findCurrentPosition(merged, patch.oldActivity());
                if (position >= 0) {
                    merged.set(position, copy(patch.upgradedActivity()));
                }
            }
        }

        merged.sort(Comparator
                .comparing(TripDto.ActivityResponse::getTime, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(TripDto.ActivityResponse::getSortOrder));
        for (int index = 0; index < merged.size(); index++) {
            merged.get(index).setId(null);
            merged.get(index).setSortOrder(index);
        }

        TripDto.DayResponse result = new TripDto.DayResponse();
        result.setDay(proposedDay.getDay());
        result.setTitle(applyAllActionable ? proposedDay.getTitle() : oldDay.getTitle());
        result.setSummary(applyAllActionable ? proposedDay.getSummary() : oldDay.getSummary());
        result.setActivities(merged);
        return result;
    }

    public String fingerprint(ItineraryDay day) {
        StringBuilder canonical = new StringBuilder();
        appendFingerprintValue(canonical, day.getDayNumber());
        appendFingerprintValue(canonical, day.getTitle());
        appendFingerprintValue(canonical, day.getSummary());
        for (TripDto.ActivityResponse activity : toResponses(day)) {
            appendFingerprintValue(canonical, activity.getId());
            appendFingerprintValue(canonical, activity.getSortOrder());
            appendFingerprintValue(canonical, activity.getTime());
            appendFingerprintValue(canonical, activity.getName());
            appendFingerprintValue(canonical, activity.getType());
            appendFingerprintValue(canonical, activity.getLocation());
            appendFingerprintValue(canonical, activity.getDuration());
            appendFingerprintValue(canonical, activity.getEstimatedCost());
            appendFingerprintValue(canonical, activity.getNote());
            appendFingerprintValue(canonical, activity.getRating());
            appendFingerprintValue(canonical, activity.getLatitude());
            appendFingerprintValue(canonical, activity.getLongitude());
            appendFingerprintValue(canonical, activity.getPlaceId());
            appendFingerprintValue(canonical, activity.getGooglePlaceId());
            appendFingerprintValue(canonical, activity.getCoordinateSource());
            appendFingerprintValue(canonical, activity.getCoordinateConfidence());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo dấu vân tay cho lịch trình", e);
        }
    }

    private void removeMatchedOldActivity(
            List<TripDto.ActivityResponse> merged,
            TripDto.RegenerateActivityChange change) {
        int position = findCurrentPosition(merged, change.getOldActivity());
        if (position < 0) {
            throw new IllegalArgumentException("Không thể xác định hoạt động cũ cần xóa");
        }
        merged.remove(position);
    }

    private void replaceMatchedOldActivity(
            List<TripDto.ActivityResponse> merged,
            TripDto.RegenerateActivityChange change) {
        int position = findCurrentPosition(merged, change.getOldActivity());
        if (position < 0) {
            throw new IllegalArgumentException("Không thể xác định hoạt động cũ cần thay đổi");
        }
        merged.set(position, copy(change.getNewActivity()));
    }

    private int findCurrentPosition(
            List<TripDto.ActivityResponse> activities,
            TripDto.ActivityResponse expected) {
        if (expected == null) {
            return -1;
        }
        if (expected.getId() != null) {
            for (int index = 0; index < activities.size(); index++) {
                if (expected.getId().equals(activities.get(index).getId())) {
                    return index;
                }
            }
        }
        for (int index = 0; index < activities.size(); index++) {
            if (sameIdentity(activities.get(index), expected)) {
                return index;
            }
        }
        return -1;
    }

    private MatchResult matchActivities(
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities,
            Map<Integer, Integer> sourceOldIndexByNewIndex) {
        List<Match> matches = new ArrayList<>();
        Set<Integer> unmatchedOld = indexes(oldActivities.size());
        Set<Integer> unmatchedNew = indexes(newActivities.size());

        int referenceMatches = 0;
        List<Map.Entry<Integer, Integer>> authoritativeEntries = sourceOldIndexByNewIndex.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (Map.Entry<Integer, Integer> entry : authoritativeEntries) {
            int newIndex = entry.getKey();
            int oldIndex = entry.getValue();
            if (oldIndex < 0 || oldIndex >= oldActivities.size()
                    || newIndex < 0 || newIndex >= newActivities.size()) {
                continue;
            }
            if (unmatchedOld.remove(oldIndex) && unmatchedNew.remove(newIndex)) {
                matches.add(new Match(oldIndex, newIndex));
                referenceMatches++;
            }
        }

        int exactMatches = 0;
        exactMatches += matchExact(
                oldActivities, newActivities, unmatchedOld, unmatchedNew, matches, ExactKey.PLACE_ID);
        exactMatches += matchExact(
                oldActivities, newActivities, unmatchedOld, unmatchedNew, matches, ExactKey.GOOGLE_PLACE_ID);
        exactMatches += matchExact(
                oldActivities, newActivities, unmatchedOld, unmatchedNew, matches, ExactKey.NAME_LOCATION);

        SemanticCandidateSelection semanticSelection =
                selectUnambiguousSemanticCandidates(oldActivities, newActivities, unmatchedOld, unmatchedNew);
        List<Integer> fuzzyOld = new ArrayList<>(unmatchedOld);
        List<Integer> fuzzyNew = new ArrayList<>(unmatchedNew);
        List<Match> fuzzyMatches = maximumWeightMatches(
                fuzzyOld,
                fuzzyNew,
                (oldIndex, newIndex) -> semanticSelection.eligibleScores()
                        .get(new CandidatePair(oldIndex, newIndex)),
                oldActivities,
                newActivities);
        for (Match match : fuzzyMatches) {
            unmatchedOld.remove(match.oldIndex());
            unmatchedNew.remove(match.newIndex());
            matches.add(match);
        }
        logSemanticMatchingDiagnostics(
                oldActivities,
                newActivities,
                fuzzyOld,
                fuzzyNew,
                fuzzyMatches,
                semanticSelection,
                unmatchedOld,
                unmatchedNew);

        matches.sort(Comparator.comparingInt(Match::oldIndex));
        return new MatchResult(
                matches,
                referenceMatches,
                exactMatches,
                fuzzyMatches.size(),
                semanticSelection.ambiguousPairs());
    }

    private int matchExact(
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities,
            Set<Integer> unmatchedOld,
            Set<Integer> unmatchedNew,
            List<Match> matches,
            ExactKey exactKey) {
        int matchedCount = 0;
        Map<String, List<Integer>> oldByKey = groupByKey(oldActivities, unmatchedOld, exactKey);
        Map<String, List<Integer>> newByKey = groupByKey(newActivities, unmatchedNew, exactKey);
        Set<String> sharedKeys = new TreeSet<>(oldByKey.keySet());
        sharedKeys.retainAll(newByKey.keySet());

        for (String key : sharedKeys) {
            List<Match> exactMatches = maximumWeightMatches(
                    oldByKey.get(key),
                    newByKey.get(key),
                    (oldIndex, newIndex) -> 100.0 + exactMatchTieScore(
                            oldActivities.get(oldIndex),
                            newActivities.get(newIndex)),
                    oldActivities,
                    newActivities);
            for (Match match : exactMatches) {
                if (unmatchedOld.remove(match.oldIndex()) && unmatchedNew.remove(match.newIndex())) {
                    matches.add(match);
                    matchedCount++;
                }
            }
        }
        return matchedCount;
    }

    private Map<String, List<Integer>> groupByKey(
            List<TripDto.ActivityResponse> activities,
            Set<Integer> indexes,
            ExactKey exactKey) {
        Map<String, List<Integer>> grouped = new HashMap<>();
        for (Integer index : indexes) {
            String key = exactKey.value(activities.get(index));
            if (!key.isBlank()) {
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
            }
        }
        return grouped;
    }

    private List<Match> maximumWeightMatches(
            List<Integer> oldIndexes,
            List<Integer> newIndexes,
            BiFunction<Integer, Integer, Double> scoreProvider,
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities) {
        if (oldIndexes.isEmpty() || newIndexes.isEmpty()) {
            return List.of();
        }
        List<Integer> sortedOld = oldIndexes.stream().sorted().toList();
        List<Integer> sortedNew = newIndexes.stream().sorted().toList();
        Map<Long, MatchingSolution> memo = new HashMap<>();
        return solveMatching(
                0,
                0,
                sortedOld,
                sortedNew,
                scoreProvider,
                oldActivities,
                newActivities,
                memo).matches();
    }

    private MatchingSolution solveMatching(
            int oldPosition,
            int usedNewMask,
            List<Integer> oldIndexes,
            List<Integer> newIndexes,
            BiFunction<Integer, Integer, Double> scoreProvider,
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities,
            Map<Long, MatchingSolution> memo) {
        if (oldPosition >= oldIndexes.size()) {
            return MatchingSolution.empty();
        }
        long memoKey = (((long) oldPosition) << 32) | Integer.toUnsignedLong(usedNewMask);
        MatchingSolution cached = memo.get(memoKey);
        if (cached != null) {
            return cached;
        }

        MatchingSolution best = solveMatching(
                oldPosition + 1,
                usedNewMask,
                oldIndexes,
                newIndexes,
                scoreProvider,
                oldActivities,
                newActivities,
                memo);
        int oldIndex = oldIndexes.get(oldPosition);
        for (int newPosition = 0; newPosition < newIndexes.size(); newPosition++) {
            int bit = 1 << newPosition;
            if ((usedNewMask & bit) != 0) {
                continue;
            }
            int newIndex = newIndexes.get(newPosition);
            Double score = scoreProvider.apply(oldIndex, newIndex);
            if (score == null) {
                continue;
            }
            MatchingSolution tail = solveMatching(
                    oldPosition + 1,
                    usedNewMask | bit,
                    oldIndexes,
                    newIndexes,
                    scoreProvider,
                    oldActivities,
                    newActivities,
                    memo);
            int timeDifference = timeDifferenceMinutes(
                    oldActivities.get(oldIndex).getTime(),
                    newActivities.get(newIndex).getTime());
            MatchingSolution candidate = tail.prepend(
                    new Match(oldIndex, newIndex),
                    score,
                    timeDifference == Integer.MAX_VALUE ? 24 * 60 : timeDifference);
            if (isBetter(candidate, best)) {
                best = candidate;
            }
        }
        memo.put(memoKey, best);
        return best;
    }

    private boolean isBetter(MatchingSolution candidate, MatchingSolution current) {
        if (candidate.totalScore() > current.totalScore() + SCORE_EPSILON) {
            return true;
        }
        if (current.totalScore() > candidate.totalScore() + SCORE_EPSILON) {
            return false;
        }
        if (candidate.matches().size() != current.matches().size()) {
            return candidate.matches().size() > current.matches().size();
        }
        if (candidate.totalTimeDifference() != current.totalTimeDifference()) {
            return candidate.totalTimeDifference() < current.totalTimeDifference();
        }
        for (int index = 0; index < Math.min(candidate.matches().size(), current.matches().size()); index++) {
            Match candidateMatch = candidate.matches().get(index);
            Match currentMatch = current.matches().get(index);
            if (candidateMatch.oldIndex() != currentMatch.oldIndex()) {
                return candidateMatch.oldIndex() < currentMatch.oldIndex();
            }
            if (candidateMatch.newIndex() != currentMatch.newIndex()) {
                return candidateMatch.newIndex() < currentMatch.newIndex();
            }
        }
        return false;
    }

    private double exactMatchTieScore(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        double name = jaccard(oldActivity.getName(), newActivity.getName());
        double location = jaccard(oldActivity.getLocation(), newActivity.getLocation());
        double type = normalize(oldActivity.getType()).equals(normalize(newActivity.getType())) ? 1.0 : 0.0;
        int timeDifference = timeDifferenceMinutes(oldActivity.getTime(), newActivity.getTime());
        double time = timeSimilarity(timeDifference);
        return name * 0.50 + location * 0.15 + type * 0.10 + time * 0.25;
    }

    private SemanticCandidateSelection selectUnambiguousSemanticCandidates(
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities,
            Set<Integer> unmatchedOld,
            Set<Integer> unmatchedNew) {
        Map<CandidatePair, Double> candidateScores = new LinkedHashMap<>();
        Map<Integer, List<ScoredCandidate>> candidatesByOld = new HashMap<>();
        Map<Integer, List<ScoredCandidate>> candidatesByNew = new HashMap<>();

        for (Integer oldIndex : unmatchedOld.stream().sorted().toList()) {
            for (Integer newIndex : unmatchedNew.stream().sorted().toList()) {
                double score = semanticSimilarity(
                        oldActivities.get(oldIndex),
                        newActivities.get(newIndex));
                if (score + SCORE_EPSILON < FUZZY_MATCH_THRESHOLD) {
                    continue;
                }
                CandidatePair pair = new CandidatePair(oldIndex, newIndex);
                candidateScores.put(pair, score);
                candidatesByOld.computeIfAbsent(oldIndex, ignored -> new ArrayList<>())
                        .add(new ScoredCandidate(pair, score));
                candidatesByNew.computeIfAbsent(newIndex, ignored -> new ArrayList<>())
                        .add(new ScoredCandidate(pair, score));
            }
        }

        Map<CandidatePair, Double> eligibleScores = new LinkedHashMap<>();
        int ambiguousPairs = 0;
        for (Map.Entry<CandidatePair, Double> entry : candidateScores.entrySet()) {
            CandidatePair pair = entry.getKey();
            List<ScoredCandidate> oldCandidates = candidatesByOld.getOrDefault(pair.oldIndex(), List.of());
            List<ScoredCandidate> newCandidates = candidatesByNew.getOrDefault(pair.newIndex(), List.of());
            boolean reciprocalUnique = oldCandidates.size() == 1 && newCandidates.size() == 1;
            boolean clearWinner = hasClearMargin(pair, oldCandidates)
                    && hasClearMargin(pair, newCandidates);
            if (reciprocalUnique || clearWinner) {
                eligibleScores.put(pair, entry.getValue());
            } else {
                ambiguousPairs++;
            }
        }
        return new SemanticCandidateSelection(candidateScores, eligibleScores, ambiguousPairs);
    }

    private void logSemanticMatchingDiagnostics(
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities,
            List<Integer> candidateOldIndexes,
            List<Integer> candidateNewIndexes,
            List<Match> semanticMatches,
            SemanticCandidateSelection semanticSelection,
            Set<Integer> unmatchedOld,
            Set<Integer> unmatchedNew) {
        if (!log.isDebugEnabled()) {
            return;
        }
        for (Match match : semanticMatches) {
            double score = semanticSelection.eligibleScores()
                    .getOrDefault(
                            new CandidatePair(match.oldIndex(), match.newIndex()),
                            semanticSimilarity(
                                    oldActivities.get(match.oldIndex()),
                                    newActivities.get(match.newIndex())));
            log.debug(
                    "Day regeneration semantic match oldIndex={}, oldTime={}, oldName={}, newIndex={}, newTime={}, newName={}, score={}",
                    match.oldIndex(),
                    safeLogText(oldActivities.get(match.oldIndex()).getTime()),
                    safeLogText(oldActivities.get(match.oldIndex()).getName()),
                    match.newIndex(),
                    safeLogText(newActivities.get(match.newIndex()).getTime()),
                    safeLogText(newActivities.get(match.newIndex()).getName()),
                    formatScore(score));
        }
        for (Integer oldIndex : unmatchedOld.stream().sorted().toList()) {
            BestSemanticCandidate best = bestCandidateForOld(
                    oldActivities,
                    newActivities,
                    oldIndex,
                    candidateNewIndexes);
            log.debug(
                    "Day regeneration unmatched old oldIndex={}, time={}, name={}, bestNewIndex={}, bestNewName={}, bestScore={}, reason={}",
                    oldIndex,
                    safeLogText(oldActivities.get(oldIndex).getTime()),
                    safeLogText(oldActivities.get(oldIndex).getName()),
                    best == null ? null : best.otherIndex(),
                    best == null ? "-" : safeLogText(newActivities.get(best.otherIndex()).getName()),
                    best == null ? "-" : formatScore(best.score()),
                    best == null ? "NO_CANDIDATE" : semanticRejectionReason(
                            new CandidatePair(oldIndex, best.otherIndex()),
                            best.score(),
                            semanticSelection));
        }
        for (Integer newIndex : unmatchedNew.stream().sorted().toList()) {
            BestSemanticCandidate best = bestCandidateForNew(
                    oldActivities,
                    newActivities,
                    newIndex,
                    candidateOldIndexes);
            log.debug(
                    "Day regeneration unmatched new newIndex={}, time={}, name={}, bestOldIndex={}, bestOldName={}, bestScore={}, reason={}",
                    newIndex,
                    safeLogText(newActivities.get(newIndex).getTime()),
                    safeLogText(newActivities.get(newIndex).getName()),
                    best == null ? null : best.otherIndex(),
                    best == null ? "-" : safeLogText(oldActivities.get(best.otherIndex()).getName()),
                    best == null ? "-" : formatScore(best.score()),
                    best == null ? "NO_CANDIDATE" : semanticRejectionReason(
                            new CandidatePair(best.otherIndex(), newIndex),
                            best.score(),
                            semanticSelection));
        }
    }

    private BestSemanticCandidate bestCandidateForOld(
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities,
            int oldIndex,
            List<Integer> newIndexes) {
        return newIndexes.stream()
                .map(newIndex -> new BestSemanticCandidate(
                        newIndex,
                        semanticSimilarity(oldActivities.get(oldIndex), newActivities.get(newIndex))))
                .max(Comparator
                        .comparingDouble(BestSemanticCandidate::score)
                        .thenComparingInt(candidate -> -candidate.otherIndex()))
                .orElse(null);
    }

    private BestSemanticCandidate bestCandidateForNew(
            List<TripDto.ActivityResponse> oldActivities,
            List<TripDto.ActivityResponse> newActivities,
            int newIndex,
            List<Integer> oldIndexes) {
        return oldIndexes.stream()
                .map(oldIndex -> new BestSemanticCandidate(
                        oldIndex,
                        semanticSimilarity(oldActivities.get(oldIndex), newActivities.get(newIndex))))
                .max(Comparator
                        .comparingDouble(BestSemanticCandidate::score)
                        .thenComparingInt(candidate -> -candidate.otherIndex()))
                .orElse(null);
    }

    private String semanticRejectionReason(
            CandidatePair pair,
            double score,
            SemanticCandidateSelection semanticSelection) {
        if (score + SCORE_EPSILON < FUZZY_MATCH_THRESHOLD) {
            return "BELOW_THRESHOLD";
        }
        if (semanticSelection.eligibleScores().containsKey(pair)) {
            return "ELIGIBLE_NOT_SELECTED";
        }
        if (semanticSelection.candidateScores().containsKey(pair)) {
            return "AMBIGUOUS";
        }
        return "NOT_ELIGIBLE";
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.3f", score);
    }

    private String safeLogText(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compact.length() <= 80 ? compact : compact.substring(0, 77) + "...";
    }

    private boolean hasClearMargin(
            CandidatePair candidate,
            List<ScoredCandidate> candidates) {
        if (candidates.isEmpty()) {
            return false;
        }
        List<ScoredCandidate> ranked = candidates.stream()
                .sorted(Comparator
                        .comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparingInt(value -> value.pair().oldIndex())
                        .thenComparingInt(value -> value.pair().newIndex()))
                .toList();
        ScoredCandidate best = ranked.get(0);
        if (!best.pair().equals(candidate)) {
            return false;
        }
        return ranked.size() == 1
                || best.score() - ranked.get(1).score() + SCORE_EPSILON >= AMBIGUITY_MARGIN;
    }

    private double semanticSimilarity(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        double name = textSemanticSimilarity(oldActivity.getName(), newActivity.getName());
        double location = textSemanticSimilarity(oldActivity.getLocation(), newActivity.getLocation());
        double combined = textSemanticSimilarity(
                String.join(" ", nullToBlank(oldActivity.getName()), nullToBlank(oldActivity.getLocation())),
                String.join(" ", nullToBlank(newActivity.getName()), nullToBlank(newActivity.getLocation())));
        double type = typeSimilarity(oldActivity.getType(), newActivity.getType());
        double time = timeSimilarity(timeDifferenceMinutes(oldActivity.getTime(), newActivity.getTime()));
        double duration = sameDuration(oldActivity.getDuration(), newActivity.getDuration()) ? 1.0 : 0.0;
        return name * 0.35
                + location * 0.15
                + combined * 0.20
                + type * 0.10
                + time * 0.15
                + duration * 0.05;
    }

    private double textSemanticSimilarity(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return 0.0;
        }
        return Math.max(
                jaccard(normalizedLeft, normalizedRight),
                Math.max(
                        dice(characterNgrams(normalizedLeft, 3), characterNgrams(normalizedRight, 3)),
                        dice(tokenBigrams(normalizedLeft), tokenBigrams(normalizedRight))));
    }

    private double typeSimilarity(String leftType, String rightType) {
        String left = normalize(leftType);
        String right = normalize(rightType);
        if (left.equals(right)) {
            return 1.0;
        }
        return EXPERIENCE_TYPES.contains(left) && EXPERIENCE_TYPES.contains(right) ? 0.7 : 0.0;
    }

    private double timeSimilarity(int timeDifference) {
        return timeDifference == Integer.MAX_VALUE
                ? 0.0
                : Math.max(0.0, 1.0 - (timeDifference / 180.0));
    }

    private List<String> changedFields(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        List<String> changed = new ArrayList<>();
        if (!sameTime(oldActivity.getTime(), newActivity.getTime())) changed.add("TIME");
        if (!normalize(oldActivity.getName()).equals(normalize(newActivity.getName()))) changed.add("NAME");
        if (!normalize(oldActivity.getType()).equals(normalize(newActivity.getType()))) changed.add("TYPE");
        if (!normalize(oldActivity.getLocation()).equals(normalize(newActivity.getLocation()))) changed.add("LOCATION");
        if (!sameDuration(oldActivity.getDuration(), newActivity.getDuration())) changed.add("DURATION");
        if (oldActivity.getEstimatedCost() != newActivity.getEstimatedCost()) changed.add("COST");
        if (!normalize(oldActivity.getNote()).equals(normalize(newActivity.getNote()))) changed.add("NOTE");
        return COMPARABLE_FIELDS.stream().filter(changed::contains).toList();
    }

    private boolean sameTime(String left, String right) {
        LocalTime leftTime = parseTime(left);
        LocalTime rightTime = parseTime(right);
        if (leftTime != null && rightTime != null) {
            return leftTime.equals(rightTime);
        }
        return normalize(left).equals(normalize(right));
    }

    private boolean sameDuration(String left, String right) {
        Integer leftMinutes = parseDurationMinutes(left);
        Integer rightMinutes = parseDurationMinutes(right);
        if (leftMinutes != null && rightMinutes != null) {
            return leftMinutes.equals(rightMinutes);
        }
        return normalize(left).equals(normalize(right));
    }

    private Integer parseDurationMinutes(String duration) {
        String normalized = normalize(duration);
        if (normalized.isBlank()) {
            return null;
        }
        int minutes = 0;
        java.util.regex.Matcher hourMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:[\\.,]\\d+)?)\\s*(gio|tieng|hours?|h)")
                .matcher(normalized);
        if (hourMatcher.find()) {
            minutes += Math.round(Float.parseFloat(hourMatcher.group(1).replace(",", ".")) * 60);
        }
        java.util.regex.Matcher minuteMatcher = java.util.regex.Pattern
                .compile("(\\d+)\\s*(phut|minutes?|mins?|p)")
                .matcher(normalized);
        if (minuteMatcher.find()) {
            minutes += Integer.parseInt(minuteMatcher.group(1));
        }
        return minutes > 0 ? minutes : null;
    }

    private double jaccard(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new HashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(normalized.split(" "))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private Set<String> characterNgrams(String value, int size) {
        String compact = normalize(value).replace(" ", "");
        if (compact.isBlank()) {
            return Set.of();
        }
        if (compact.length() <= size) {
            return Set.of(compact);
        }
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index <= compact.length() - size; index++) {
            result.add(compact.substring(index, index + size));
        }
        return result;
    }

    private Set<String> tokenBigrams(String value) {
        List<String> tokenList = java.util.Arrays.stream(normalize(value).split(" "))
                .filter(token -> !token.isBlank())
                .toList();
        if (tokenList.isEmpty()) {
            return Set.of();
        }
        if (tokenList.size() == 1) {
            return Set.of(tokenList.get(0));
        }
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < tokenList.size() - 1; index++) {
            result.add(tokenList.get(index) + " " + tokenList.get(index + 1));
        }
        return result;
    }

    private double dice(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        return (2.0 * intersection.size()) / (left.size() + right.size());
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("đ", "d")
                .replace("Đ", "D")
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (Exception ignored) {
            try {
                return LocalTime.parse(value.trim(), java.time.format.DateTimeFormatter.ofPattern("H:mm"));
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private int timeDifferenceMinutes(String left, String right) {
        LocalTime leftTime = parseTime(left);
        LocalTime rightTime = parseTime(right);
        if (leftTime == null || rightTime == null) {
            return Integer.MAX_VALUE;
        }
        return Math.abs((int) java.time.Duration.between(leftTime, rightTime).toMinutes());
    }

    private boolean sameIdentity(
            TripDto.ActivityResponse left,
            TripDto.ActivityResponse right) {
        if (left.getPlaceId() != null && left.getPlaceId().equals(right.getPlaceId())) {
            return true;
        }
        if (!normalizeIdentifier(left.getGooglePlaceId()).isBlank()
                && normalizeIdentifier(left.getGooglePlaceId()).equals(normalizeIdentifier(right.getGooglePlaceId()))) {
            return true;
        }
        return ExactKey.NAME_LOCATION.value(left).equals(ExactKey.NAME_LOCATION.value(right));
    }

    private void appendFingerprintValue(StringBuilder target, Object value) {
        String text = value == null ? "<null>" : value.toString();
        target.append(text.length()).append(':').append(text).append('|');
    }

    private String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim();
    }

    private List<TripDto.ActivityResponse> toResponses(ItineraryDay day) {
        if (day.getActivities() == null) {
            return List.of();
        }
        return day.getActivities().stream()
                .sorted(Comparator.comparingInt(activity -> activity.getSortOrder() != null
                        ? activity.getSortOrder()
                        : Integer.MAX_VALUE))
                .map(TripDto.ActivityResponse::from)
                .toList();
    }

    private TripDto.ActivityResponse copy(TripDto.ActivityResponse source) {
        if (source == null) {
            return null;
        }
        TripDto.ActivityResponse copy = new TripDto.ActivityResponse();
        copy.setId(source.getId());
        copy.setTime(source.getTime());
        copy.setName(source.getName());
        copy.setType(source.getType());
        copy.setLocation(source.getLocation());
        copy.setDuration(source.getDuration());
        copy.setEstimatedCost(source.getEstimatedCost());
        copy.setCostEstimateStatus(source.getCostEstimateStatus());
        copy.setCostEstimateMessage(source.getCostEstimateMessage());
        copy.setNote(source.getNote());
        copy.setRating(source.getRating());
        copy.setLatitude(source.getLatitude());
        copy.setLongitude(source.getLongitude());
        copy.setPlaceId(source.getPlaceId());
        copy.setGooglePlaceId(source.getGooglePlaceId());
        copy.setCoordinateSource(source.getCoordinateSource());
        copy.setCoordinateConfidence(source.getCoordinateConfidence());
        copy.setSortOrder(source.getSortOrder());
        return copy;
    }

    private Set<Integer> indexes(int size) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int index = 0; index < size; index++) {
            result.add(index);
        }
        return result;
    }

    public record DiffResult(
            List<TripDto.RegenerateActivityChange> changes,
            List<TripDto.RegenerateUnchangedActivity> unchangedActivities,
            List<ActivityMetadataReconciliationService.MetadataPatch> metadataPatches,
            String oldDayFingerprint,
            MatchingDiagnostics diagnostics) {
        public int unchangedActivityCount() {
            return unchangedActivities.size();
        }

        public int metadataUpgradeCount() {
            return metadataPatches.size();
        }
    }

    public record MatchingDiagnostics(
            int referenceMatches,
            int exactMatches,
            int semanticMatches,
            int ambiguousPairs,
            int added,
            int removed) {
    }

    private record Match(int oldIndex, int newIndex) {
    }

    private record MatchResult(
            List<Match> matches,
            int referenceMatches,
            int exactMatches,
            int semanticMatches,
            int ambiguousPairs) {
    }

    private record CandidatePair(int oldIndex, int newIndex) {
    }

    private record ScoredCandidate(CandidatePair pair, double score) {
    }

    private record SemanticCandidateSelection(
            Map<CandidatePair, Double> candidateScores,
            Map<CandidatePair, Double> eligibleScores,
            int ambiguousPairs) {
    }

    private record BestSemanticCandidate(int otherIndex, double score) {
    }

    private record MatchingSolution(
            double totalScore,
            int totalTimeDifference,
            List<Match> matches) {
        private static MatchingSolution empty() {
            return new MatchingSolution(0.0, 0, List.of());
        }

        private MatchingSolution prepend(Match match, double score, int timeDifference) {
            List<Match> combined = new ArrayList<>(matches.size() + 1);
            combined.add(match);
            combined.addAll(matches);
            return new MatchingSolution(
                    totalScore + score,
                    totalTimeDifference + timeDifference,
                    List.copyOf(combined));
        }
    }

    private record PendingChange(
            String type,
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity,
            List<String> changedFields,
            Integer oldIndex,
            Integer newIndex) {
        private String displayTime() {
            return newActivity != null ? newActivity.getTime() : oldActivity != null ? oldActivity.getTime() : null;
        }
    }

    private enum ExactKey {
        PLACE_ID {
            @Override
            String value(TripDto.ActivityResponse activity) {
                return activity.getPlaceId() == null ? "" : activity.getPlaceId().toString();
            }
        },
        GOOGLE_PLACE_ID {
            @Override
            String value(TripDto.ActivityResponse activity) {
                return activity.getGooglePlaceId() == null ? "" : activity.getGooglePlaceId().trim();
            }
        },
        NAME_LOCATION {
            @Override
            String value(TripDto.ActivityResponse activity) {
                String name = normalizeStatic(activity.getName());
                String location = normalizeStatic(activity.getLocation());
                return name.isBlank() ? "" : name + "|" + location;
            }
        };

        abstract String value(TripDto.ActivityResponse activity);

        private static String normalizeStatic(String value) {
            if (value == null) {
                return "";
            }
            String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replace("đ", "d")
                    .replace("Đ", "D")
                    .replaceAll("\\p{M}+", "");
            return decomposed.toLowerCase(Locale.ROOT)
                    .replaceAll("[^\\p{L}\\p{N}]+", " ")
                    .trim()
                    .replaceAll("\\s+", " ");
        }
    }
}
