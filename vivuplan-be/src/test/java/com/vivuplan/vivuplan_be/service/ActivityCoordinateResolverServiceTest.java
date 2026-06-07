package com.vivuplan.vivuplan_be.service;

import com.vivuplan.vivuplan_be.dto.TripDto;
import com.vivuplan.vivuplan_be.entity.Activity;
import com.vivuplan.vivuplan_be.entity.ItineraryDay;
import com.vivuplan.vivuplan_be.entity.LocationResolutionCache;
import com.vivuplan.vivuplan_be.entity.Trip;
import com.vivuplan.vivuplan_be.repository.LocationResolutionCacheRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityCoordinateResolverServiceTest {

    @Mock
    private LocationResolutionCacheRepository cacheRepository;

    @Mock
    private RestTemplate restTemplate;

    @Test
    void resolveScheduleUsesLocationAndAppliesConfidentResult() {
        ActivityCoordinateResolverService service = service(true);
        TripDto.ActivityResponse activity = activity("ATTRACTION", "Nha hang Song Chay");
        when(cacheRepository.findByProviderAndNormalizedQuery(eq("NOMINATIM"), any()))
                .thenReturn(Optional.empty());
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), any(Class.class)))
                .thenReturn(ResponseEntity.ok(List.of(candidate(
                        "Khu du lich Song Chay - Hang Toi, Bo Trach, Quang Binh, Viet Nam",
                        "17.5980",
                        "106.2671",
                        "tourism",
                        "attraction"))));

        ActivityCoordinateResolverService.BatchResult result = service.resolveSchedule(List.of(day(activity)), "Phong Nha");

        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo("SUCCESS");
                    assertThat(item.applied()).isTrue();
                });
        assertThat(activity.getLatitude()).isEqualTo(17.5980);
        assertThat(activity.getLongitude()).isEqualTo(106.2671);
        assertThat(activity.getCoordinateSource()).isEqualTo(Activity.CoordinateSource.GEOCODED_LOCATION.name());
        assertThat(activity.getCoordinateConfidence()).isEqualTo(Activity.CoordinateConfidence.MEDIUM.name());
        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo("SUCCESS");
                    assertThat(item.query()).contains("Nha hang Song Chay", "Phong Nha");
                });

        ArgumentCaptor<LocationResolutionCache> cacheCaptor = ArgumentCaptor.forClass(LocationResolutionCache.class);
        verify(cacheRepository).save(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue().getStatus()).isEqualTo(LocationResolutionCache.Status.SUCCESS);
    }

    @Test
    void cacheHitDoesNotCallExternalGeocoder() {
        ActivityCoordinateResolverService service = service(true);
        TripDto.ActivityResponse activity = activity("ATTRACTION", "Ben thuyen Trang An");
        LocationResolutionCache cache = LocationResolutionCache.builder()
                .provider("NOMINATIM")
                .normalizedQuery("ben thuyen trang an ninh binh viet nam")
                .status(LocationResolutionCache.Status.SUCCESS)
                .latitude(20.2520)
                .longitude(105.9180)
                .displayName("Ben thuyen Trang An, Ninh Binh, Viet Nam")
                .confidence(80)
                .build();
        when(cacheRepository.findByProviderAndNormalizedQuery(eq("NOMINATIM"), any()))
                .thenReturn(Optional.of(cache));

        service.resolveSchedule(List.of(day(activity)), "Ninh Bình");

        assertThat(activity.getLatitude()).isEqualTo(20.2520);
        assertThat(activity.getLongitude()).isEqualTo(105.9180);
        verify(restTemplate, never()).exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }

    @Test
    void skipsTransportAndGenericLocations() {
        ActivityCoordinateResolverService service = service(true);
        TripDto.ActivityResponse transport = activity("TRANSPORT", "Hà Nội");
        TripDto.ActivityResponse genericFood = activity("FOOD", "Nha hang gan do");
        TripDto.ActivityResponse broadArea = activity("ATTRACTION", "Trung tam thanh pho");

        ActivityCoordinateResolverService.BatchResult result = service.resolveSchedule(
                List.of(day(transport, genericFood, broadArea)),
                "Hạ Long");

        assertThat(result.items()).extracting(ActivityCoordinateResolverService.ItemResult::status)
                .containsExactly("SKIPPED", "SKIPPED", "SKIPPED");
        verify(restTemplate, never()).exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }

    @Test
    void allowsNamedPlaceEvenWhenItStartsWithBroadAreaWords() {
        ActivityCoordinateResolverService service = service(true);
        TripDto.ActivityResponse activity = activity("ATTRACTION", "Khu du lich Song Chay");
        when(cacheRepository.findByProviderAndNormalizedQuery(eq("NOMINATIM"), any()))
                .thenReturn(Optional.empty());
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), any(Class.class)))
                .thenReturn(ResponseEntity.ok(List.of(candidate(
                        "Khu du lich Song Chay - Hang Toi, Bo Trach, Quang Binh, Viet Nam",
                        "17.5980",
                        "106.2671",
                        "tourism",
                        "attraction"))));

        ActivityCoordinateResolverService.BatchResult result = service.resolveSchedule(List.of(day(activity)), "Phong Nha");

        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo("SUCCESS");
                    assertThat(item.applied()).isTrue();
                });
        assertThat(activity.getLatitude()).isEqualTo(17.5980);
    }

    @Test
    void buildQueriesDoesNotDuplicateDestinationAlreadyPresentInLocation() throws Exception {
        ActivityCoordinateResolverService service = service(true);

        List<String> queries = buildQueries(
                service,
                "Khu suối khoáng nóng, Khu du lịch sinh thái Mường Thanh Diễn Lâm",
                "Khu du lịch sinh thái Mường Thanh Diễn Lâm, Nghệ An");

        assertThat(queries)
                .containsExactly("Khu suối khoáng nóng, Khu du lịch sinh thái Mường Thanh Diễn Lâm, Việt Nam");
    }

    @Test
    void buildQueriesKeepsDestinationContextForPlainLocation() throws Exception {
        ActivityCoordinateResolverService service = service(true);

        List<String> queries = buildQueries(service, "Cầu Vàng", "Đà Nẵng");

        assertThat(queries).containsExactly(
                "Cầu Vàng, Đà Nẵng, Việt Nam",
                "Cầu Vàng, Việt Nam");
    }

    @Test
    void buildQueriesRecognizesDestinationWithEquivalentSpacing() throws Exception {
        ActivityCoordinateResolverService service = service(true);

        List<String> queries = buildQueries(service, "Chợ đêm Sa Pa", "Sapa");

        assertThat(queries).containsExactly("Chợ đêm Sa Pa, Việt Nam");
    }

    @Test
    void buildQueriesRecognizesShortDestinationAlreadyPresentInLocation() throws Exception {
        ActivityCoordinateResolverService service = service(true);

        List<String> queries = buildQueries(service, "Chợ Đông Ba, Huế", "Huế");

        assertThat(queries).containsExactly("Chợ Đông Ba, Huế, Việt Nam");
    }

    @Test
    void dryRunReportsIncompleteExistingCoordinatesWithoutCallingExternalGeocoder() {
        ActivityCoordinateResolverService service = service(true);
        Activity activity = Activity.builder()
                .id(100L)
                .name("Tham quan dia diem")
                .time("09:00")
                .type(Activity.ActivityType.ATTRACTION)
                .location("Ben thuyen Trang An")
                .sortOrder(0)
                .latitude(20.2520)
                .build();
        ItineraryDay day = ItineraryDay.builder()
                .dayNumber(1)
                .activities(List.of(activity))
                .build();
        Trip trip = Trip.builder()
                .id(6L)
                .destination("Ninh Binh")
                .itineraryDays(List.of(day))
                .build();

        ActivityCoordinateResolverService.BatchResult result = service.resolveTrip(trip, true);

        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo("INVALID_COORDINATES");
                    assertThat(item.applied()).isFalse();
                });
        assertThat(activity.getLatitude()).isEqualTo(20.2520);
        assertThat(activity.getLongitude()).isNull();
        verify(restTemplate, never()).exchange(any(URI.class), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }

    @Test
    void lowConfidenceResultDoesNotMutateActivity() {
        ActivityCoordinateResolverService service = service(true);
        TripDto.ActivityResponse activity = activity("ATTRACTION", "Mot dia diem rat mo ho");
        when(cacheRepository.findByProviderAndNormalizedQuery(eq("NOMINATIM"), any()))
                .thenReturn(Optional.empty());
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), any(Class.class)))
                .thenReturn(ResponseEntity.ok(List.of(candidate(
                        "Ha Noi, Viet Nam",
                        "21.0285",
                        "105.8542",
                        "place",
                        "city"))));

        ActivityCoordinateResolverService.BatchResult result = service.resolveSchedule(List.of(day(activity)), "Đà Nẵng");

        assertThat(activity.getLatitude()).isNull();
        assertThat(activity.getLongitude()).isNull();
        assertThat(result.items().get(result.items().size() - 1).status()).isEqualTo("LOW_CONFIDENCE");
    }

    @Test
    void dryRunReportsResultWithoutMutatingTrip() {
        ActivityCoordinateResolverService service = service(true);
        Activity activity = Activity.builder()
                .id(99L)
                .name("Tham quan Song Chay")
                .time("09:00")
                .type(Activity.ActivityType.ATTRACTION)
                .location("Song Chay Hang Toi")
                .sortOrder(0)
                .build();
        ItineraryDay day = ItineraryDay.builder()
                .dayNumber(1)
                .activities(List.of(activity))
                .build();
        Trip trip = Trip.builder()
                .id(5L)
                .destination("Phong Nha")
                .itineraryDays(List.of(day))
                .build();
        when(cacheRepository.findByProviderAndNormalizedQuery(eq("NOMINATIM"), any()))
                .thenReturn(Optional.empty());
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), any(Class.class)))
                .thenReturn(ResponseEntity.ok(List.of(candidate(
                        "Song Chay - Hang Toi, Bo Trach, Quang Binh, Viet Nam",
                        "17.5980",
                        "106.2671",
                        "tourism",
                        "attraction"))));

        ActivityCoordinateResolverService.BatchResult result = service.resolveTrip(trip, true);

        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo("SUCCESS");
                    assertThat(item.applied()).isFalse();
                });
        assertThat(result.appliedCount()).isZero();
        assertThat(activity.getLatitude()).isNull();
        assertThat(activity.getLongitude()).isNull();
        verify(cacheRepository, never()).save(any(LocationResolutionCache.class));
    }

    private ActivityCoordinateResolverService service(boolean enabled) {
        return new ActivityCoordinateResolverService(
                cacheRepository,
                restTemplate,
                enabled,
                "https://nominatim.example/search",
                20,
                0);
    }

    @SuppressWarnings("unchecked")
    private List<String> buildQueries(ActivityCoordinateResolverService service, String location, String destination)
            throws Exception {
        Method method = ActivityCoordinateResolverService.class.getDeclaredMethod(
                "buildQueries",
                String.class,
                String.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, location, destination);
    }

    private TripDto.DayResponse day(TripDto.ActivityResponse... activities) {
        TripDto.DayResponse day = new TripDto.DayResponse();
        day.setDay(1);
        day.setActivities(List.of(activities));
        return day;
    }

    private TripDto.ActivityResponse activity(String type, String location) {
        TripDto.ActivityResponse activity = new TripDto.ActivityResponse();
        activity.setName("Activity at " + location);
        activity.setTime("09:00");
        activity.setType(type);
        activity.setLocation(location);
        activity.setSortOrder(0);
        return activity;
    }

    private Map<String, Object> candidate(String displayName, String lat, String lon, String category, String type) {
        return Map.of(
                "display_name", displayName,
                "lat", lat,
                "lon", lon,
                "category", category,
                "type", type,
                "importance", 0.45);
    }
}
