package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ActivityMetadataReconciliationService {

    private static final Set<String> EXPERIENCE_TYPES = Set.of(
            "food", "cafe", "attraction", "activity", "nightlife");

    public TripDto.ActivityResponse reconcileModifiedActivity(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity,
            List<String> changedFields) {
        TripDto.ActivityResponse resolved = copy(newActivity);
        boolean placeFieldsChanged = changedFields != null
                && (changedFields.contains("NAME") || changedFields.contains("LOCATION"));
        PlaceRelation stableIdentifierRelation = compareStableIdentifiers(oldActivity, newActivity);

        if (!placeFieldsChanged && stableIdentifierRelation == PlaceRelation.DIFFERENT_PLACE) {
            copyMetadata(oldActivity, resolved);
            return resolved;
        }

        PlaceRelation placeRelation = placeFieldsChanged
                ? determinePlaceRelation(oldActivity, newActivity)
                : PlaceRelation.SAME_PLACE;
        if (placeRelation != PlaceRelation.SAME_PLACE) {
            return resolved;
        }

        TripDto.ActivityResponse trustedMetadata = buildPatch(oldActivity, newActivity, 0)
                .map(MetadataPatch::upgradedActivity)
                .orElseGet(() -> copy(oldActivity));
        copyMetadata(trustedMetadata, resolved);
        return resolved;
    }

    private PlaceRelation determinePlaceRelation(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        PlaceRelation identifierRelation = compareStableIdentifiers(oldActivity, newActivity);
        if (identifierRelation != PlaceRelation.UNCERTAIN) {
            return identifierRelation;
        }

        return determineSemanticPlaceRelation(oldActivity, newActivity);
    }

    private PlaceRelation compareStableIdentifiers(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        String oldGooglePlaceId = normalizeIdentifier(oldActivity.getGooglePlaceId());
        String newGooglePlaceId = normalizeIdentifier(newActivity.getGooglePlaceId());
        if (!oldGooglePlaceId.isBlank()
                && oldGooglePlaceId.equals(newGooglePlaceId)) {
            return PlaceRelation.SAME_PLACE;
        }

        if (oldActivity.getPlaceId() != null && newActivity.getPlaceId() != null) {
            return oldActivity.getPlaceId().equals(newActivity.getPlaceId())
                    ? PlaceRelation.SAME_PLACE
                    : PlaceRelation.DIFFERENT_PLACE;
        }
        return PlaceRelation.UNCERTAIN;
    }

    private PlaceRelation determineSemanticPlaceRelation(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        String oldName = normalizeText(oldActivity.getName());
        String newName = normalizeText(newActivity.getName());
        String oldLocation = normalizeText(oldActivity.getLocation());
        String newLocation = normalizeText(newActivity.getLocation());
        double nameSimilarity = textSimilarity(oldName, newName);
        double typeCompatibility = typeCompatibility(oldActivity.getType(), newActivity.getType());

        if (oldLocation.isBlank() && newLocation.isBlank()) {
            return nameSimilarity >= 0.85 && typeCompatibility > 0
                    ? PlaceRelation.SAME_PLACE
                    : PlaceRelation.UNCERTAIN;
        }
        if (oldLocation.isBlank() || newLocation.isBlank()) {
            return PlaceRelation.UNCERTAIN;
        }

        double locationSimilarity = textSimilarity(oldLocation, newLocation);
        boolean sameName = !oldName.isBlank() && oldName.equals(newName);
        boolean sameLocation = oldLocation.equals(newLocation);
        if (sameName && sameLocation) {
            return PlaceRelation.SAME_PLACE;
        }
        if (sameLocation && nameSimilarity >= 0.60) {
            return PlaceRelation.SAME_PLACE;
        }
        if (sameName && locationSimilarity >= 0.60) {
            return PlaceRelation.SAME_PLACE;
        }

        double combinedEvidence = nameSimilarity * 0.50
                + locationSimilarity * 0.35
                + typeCompatibility * 0.15;
        return combinedEvidence >= 0.68
                && nameSimilarity >= 0.50
                && locationSimilarity >= 0.50
                ? PlaceRelation.SAME_PLACE
                : PlaceRelation.UNCERTAIN;
    }

    private double typeCompatibility(String oldType, String newType) {
        String oldNormalized = normalizeEnum(oldType).toLowerCase(Locale.ROOT);
        String newNormalized = normalizeEnum(newType).toLowerCase(Locale.ROOT);
        if (oldNormalized.isBlank() || newNormalized.isBlank()) {
            return 0.0;
        }
        if (oldNormalized.equals(newNormalized)) {
            return 1.0;
        }
        return EXPERIENCE_TYPES.contains(oldNormalized) && EXPERIENCE_TYPES.contains(newNormalized)
                ? 0.6
                : 0.0;
    }

    private double textSimilarity(String left, String right) {
        String leftNormalized = normalizeText(left);
        String rightNormalized = normalizeText(right);
        if (leftNormalized.isBlank() || rightNormalized.isBlank()) {
            return 0.0;
        }
        if (leftNormalized.equals(rightNormalized)) {
            return 1.0;
        }
        return Math.max(
                tokenJaccard(leftNormalized, rightNormalized),
                dice(characterNgrams(leftNormalized, 3), characterNgrams(rightNormalized, 3)));
    }

    private double tokenJaccard(String left, String right) {
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

    private Set<String> characterNgrams(String value, int size) {
        String compact = normalizeText(value).replace(" ", "");
        if (compact.isBlank()) {
            return Set.of();
        }
        if (compact.length() <= size) {
            return Set.of(compact);
        }
        Set<String> result = new HashSet<>();
        for (int index = 0; index <= compact.length() - size; index++) {
            result.add(compact.substring(index, index + size));
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

    private Set<String> tokens(String value) {
        return Arrays.stream(value.split(" "))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalizeText(String value) {
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

    private String normalizeIdentifier(String value) {
        return value == null ? "" : value.trim();
    }

    public Optional<MetadataPatch> buildPatch(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity,
            int oldIndex) {
        TripDto.ActivityResponse upgraded = copy(oldActivity);
        boolean conflictingVerifiedIdentity = hasConflictingVerifiedIdentity(oldActivity, newActivity);
        boolean changed = false;

        if (!conflictingVerifiedIdentity
                && shouldUseCandidateCoordinates(oldActivity, newActivity)
                && coordinateMetadataDiffers(oldActivity, newActivity)) {
            upgraded.setLatitude(newActivity.getLatitude());
            upgraded.setLongitude(newActivity.getLongitude());
            upgraded.setCoordinateSource(newActivity.getCoordinateSource());
            upgraded.setCoordinateConfidence(newActivity.getCoordinateConfidence());
            if (hasVerifiedPlaceReference(newActivity)) {
                upgraded.setPlaceId(newActivity.getPlaceId());
                if (!isBlank(newActivity.getGooglePlaceId())) {
                    upgraded.setGooglePlaceId(newActivity.getGooglePlaceId());
                }
            }
            changed = true;
        }

        if (!conflictingVerifiedIdentity && hasVerifiedPlaceReference(newActivity)) {
            if (!newActivity.getPlaceId().equals(upgraded.getPlaceId())) {
                upgraded.setPlaceId(newActivity.getPlaceId());
                changed = true;
            }
            if (!isBlank(newActivity.getGooglePlaceId())
                    && !newActivity.getGooglePlaceId().trim().equals(normalizeIdentifier(upgraded.getGooglePlaceId()))) {
                upgraded.setGooglePlaceId(newActivity.getGooglePlaceId());
                changed = true;
            }
            if (newActivity.getRating() > 0
                    && Double.compare(upgraded.getRating(), newActivity.getRating()) != 0) {
                upgraded.setRating(newActivity.getRating());
                changed = true;
            }
        }

        return changed
                ? Optional.of(new MetadataPatch(oldIndex, copy(oldActivity), upgraded))
                : Optional.empty();
    }

    private boolean shouldUseCandidateCoordinates(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        if (!hasValidCoordinate(newActivity)) {
            return false;
        }
        if ("MANUAL".equals(normalizeEnum(oldActivity.getCoordinateSource()))) {
            return false;
        }
        if (isVerifiedCoordinate(newActivity)) {
            return true;
        }
        int oldTrust = coordinateTrust(oldActivity);
        int newTrust = coordinateTrust(newActivity);
        if (!hasValidCoordinate(oldActivity)) {
            return newTrust > 0;
        }
        if (newTrust != oldTrust) {
            return newTrust > oldTrust;
        }
        return confidenceTrust(newActivity.getCoordinateConfidence())
                > confidenceTrust(oldActivity.getCoordinateConfidence());
    }

    private boolean coordinateMetadataDiffers(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        return !java.util.Objects.equals(oldActivity.getLatitude(), newActivity.getLatitude())
                || !java.util.Objects.equals(oldActivity.getLongitude(), newActivity.getLongitude())
                || !normalizeEnum(oldActivity.getCoordinateSource())
                .equals(normalizeEnum(newActivity.getCoordinateSource()))
                || !normalizeEnum(oldActivity.getCoordinateConfidence())
                .equals(normalizeEnum(newActivity.getCoordinateConfidence()));
    }

    private int coordinateTrust(TripDto.ActivityResponse activity) {
        if (!hasValidCoordinate(activity)) {
            return 0;
        }
        return switch (normalizeEnum(activity.getCoordinateSource())) {
            case "MANUAL" -> 4;
            case "VERIFIED_PLACE" -> isVerifiedCoordinate(activity) ? 3 : 0;
            case "GEOCODED_LOCATION" -> 2;
            case "AI_PROVIDED" -> 1;
            default -> 0;
        };
    }

    private int confidenceTrust(String confidence) {
        return switch (normalizeEnum(confidence)) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private boolean isVerifiedCoordinate(TripDto.ActivityResponse activity) {
        return "VERIFIED_PLACE".equals(normalizeEnum(activity.getCoordinateSource()))
                && hasVerifiedPlaceReference(activity)
                && hasValidCoordinate(activity);
    }

    private boolean hasVerifiedPlaceReference(TripDto.ActivityResponse activity) {
        return activity.getPlaceId() != null;
    }

    private boolean hasConflictingVerifiedIdentity(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity) {
        return compareStableIdentifiers(oldActivity, newActivity)
                == PlaceRelation.DIFFERENT_PLACE;
    }

    private boolean hasValidCoordinate(TripDto.ActivityResponse activity) {
        return activity.getLatitude() != null
                && activity.getLongitude() != null
                && Double.isFinite(activity.getLatitude())
                && Double.isFinite(activity.getLongitude())
                && activity.getLatitude() >= -90
                && activity.getLatitude() <= 90
                && activity.getLongitude() >= -180
                && activity.getLongitude() <= 180
                && !(Math.abs(activity.getLatitude()) < 0.000_001
                && Math.abs(activity.getLongitude()) < 0.000_001);
    }

    private String normalizeEnum(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void copyMetadata(
            TripDto.ActivityResponse source,
            TripDto.ActivityResponse target) {
        target.setRating(source.getRating());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setPlaceId(source.getPlaceId());
        target.setGooglePlaceId(source.getGooglePlaceId());
        target.setCoordinateSource(source.getCoordinateSource());
        target.setCoordinateConfidence(source.getCoordinateConfidence());
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

    public record MetadataPatch(
            int oldIndex,
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse upgradedActivity) {
    }

    private enum PlaceRelation {
        SAME_PLACE,
        DIFFERENT_PLACE,
        UNCERTAIN
    }
}
