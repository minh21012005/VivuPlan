package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class ActivityMetadataReconciliationService {

    public Optional<MetadataPatch> buildPatch(
            TripDto.ActivityResponse oldActivity,
            TripDto.ActivityResponse newActivity,
            int oldIndex) {
        TripDto.ActivityResponse upgraded = copy(oldActivity);
        boolean conflictingVerifiedIdentity = hasConflictingVerifiedIdentity(oldActivity, newActivity);
        boolean changed = false;

        if (!conflictingVerifiedIdentity && shouldUseCandidateCoordinates(oldActivity, newActivity)) {
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
            if (upgraded.getPlaceId() == null) {
                upgraded.setPlaceId(newActivity.getPlaceId());
                changed = true;
            }
            if (isBlank(upgraded.getGooglePlaceId()) && !isBlank(newActivity.getGooglePlaceId())) {
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
        if (!hasVerifiedPlaceReference(oldActivity) || !hasVerifiedPlaceReference(newActivity)) {
            return false;
        }
        if (!oldActivity.getPlaceId().equals(newActivity.getPlaceId())) {
            return true;
        }
        return !isBlank(oldActivity.getGooglePlaceId())
                && !isBlank(newActivity.getGooglePlaceId())
                && !oldActivity.getGooglePlaceId().trim().equals(newActivity.getGooglePlaceId().trim());
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

    private TripDto.ActivityResponse copy(TripDto.ActivityResponse source) {
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
}
