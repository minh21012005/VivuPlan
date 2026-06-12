package com.vivuplan.vivuplan_be.repository;

import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.AiUsageLog;
import com.vivuplan.vivuplan_be.entity.CreditLedger;
import com.vivuplan.vivuplan_be.entity.ItineraryDay;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class TripDeletionPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private CreditLedgerRepository creditLedgerRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Test
    void detachesAuditHistoryAndCascadesScheduleBeforeDeletingTrip() {
        User user = entityManager.persistAndFlush(User.builder()
                .name("Delete Test")
                .email("delete-test@example.com")
                .emailVerified(true)
                .build());

        Trip trip = Trip.builder()
                .user(user)
                .destination("Ba Vi")
                .days(1)
                .budgetPerPerson(1_000_000L)
                .travelerCount(2)
                .status(Trip.TripStatus.PLANNED)
                .shareCode("DELETE_TEST")
                .build();
        ItineraryDay day = ItineraryDay.builder()
                .trip(trip)
                .dayNumber(1)
                .title("Day 1")
                .build();
        Activity activity = Activity.builder()
                .itineraryDay(day)
                .name("Visit Ba Vi")
                .time("08:00")
                .type(Activity.ActivityType.ATTRACTION)
                .sortOrder(0)
                .build();
        day.setActivities(new ArrayList<>(List.of(activity)));
        trip.setItineraryDays(new ArrayList<>(List.of(day)));
        trip = tripRepository.saveAndFlush(trip);

        CreditLedger ledger = creditLedgerRepository.saveAndFlush(CreditLedger.builder()
                .user(user)
                .type(CreditLedger.CreditType.PLAN)
                .delta(-1L)
                .reason("PLAN_GENERATION")
                .trip(trip)
                .build());
        AiUsageLog usageLog = aiUsageLogRepository.saveAndFlush(AiUsageLog.builder()
                .operation(AiUsageLog.Operation.PLAN_GENERATION)
                .status(AiUsageLog.Status.SUCCESS)
                .requestId("delete-trip-test")
                .attemptNumber(1)
                .user(user)
                .trip(trip)
                .model("test-model")
                .inputUsdPer1M(BigDecimal.ONE)
                .outputUsdPer1M(BigDecimal.ONE)
                .usdToVndRate(BigDecimal.valueOf(25_000))
                .estimatedCostUsd(BigDecimal.valueOf(0.001))
                .estimatedCostVnd(25L)
                .build());

        Long tripId = trip.getId();
        Long dayId = day.getId();
        Long activityId = activity.getId();
        Long ledgerId = ledger.getId();
        Long usageLogId = usageLog.getId();

        creditLedgerRepository.detachFromTrip(tripId);
        aiUsageLogRepository.detachFromTrip(tripId);
        tripRepository.delete(trip);
        tripRepository.flush();
        entityManager.clear();

        assertThat(tripRepository.findById(tripId)).isEmpty();
        assertThat(entityManager.find(ItineraryDay.class, dayId)).isNull();
        assertThat(entityManager.find(Activity.class, activityId)).isNull();
        assertThat(creditLedgerRepository.findById(ledgerId))
                .get()
                .extracting(CreditLedger::getTrip)
                .isNull();
        assertThat(aiUsageLogRepository.findById(usageLogId))
                .get()
                .extracting(AiUsageLog::getTrip)
                .isNull();
    }

    @Test
    void shareCodeLookupReturnsOnlyCurrentlyPublicTrips() {
        User user = entityManager.persistAndFlush(User.builder()
                .name("Share Test")
                .email("share-test@example.com")
                .emailVerified(true)
                .build());
        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .user(user)
                .destination("Da Nang")
                .days(1)
                .budgetPerPerson(1_000_000L)
                .shareCode("S123456789")
                .isPublic(false)
                .build());

        assertThat(tripRepository.findByShareCodeAndIsPublicTrue("S123456789")).isEmpty();

        trip.setIsPublic(true);
        tripRepository.saveAndFlush(trip);
        entityManager.clear();

        assertThat(tripRepository.findByShareCodeAndIsPublicTrue("S123456789"))
                .isPresent()
                .get()
                .extracting(Trip::getId)
                .isEqualTo(trip.getId());
    }
}
