package com.example.bodeul.ui.booking;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.data.map.KakaoPlaceCoordinate;
import com.example.bodeul.domain.model.BookingHospitalOption;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class BookingHospitalSelectorCoordinatorTest {
    private final BookingHospitalSelectorCoordinator coordinator = new BookingHospitalSelectorCoordinator(null);
    private final BookingHospitalCatalog catalog = new BookingHospitalCatalog(Arrays.asList(
            new BookingHospitalOption("서울 병원", Arrays.asList("내과", "외과")),
            new BookingHospitalOption("서울 치과", Arrays.asList("치과"))));

    @Test public void emptyCatalogStillAllowsRemoteSearch() {
        FakeSearch search = new FakeSearch();
        search.results = Arrays.asList(new KakaoPlaceCoordinate("새 병원", 37.5, 127.1));
        List<BookingHospitalOption> result = run("새", BookingHospitalCatalog.empty(), search);
        assertEquals(1, result.size());
        assertEquals(37.5, result.get(0).getLatitude(), 0.00001);
        assertTrue(result.get(0).getDepartmentNames().isEmpty());
    }
    @Test public void remoteCoordinatesAreCombinedWithExistingDepartments() {
        FakeSearch search = new FakeSearch();
        search.results = Arrays.asList(new KakaoPlaceCoordinate("서울 병원", 37.5, 127.1));
        List<BookingHospitalOption> result = run("서울", catalog, search);
        assertEquals(2, result.size());
        assertEquals(Arrays.asList("내과", "외과"), result.get(0).getDepartmentNames());
        assertEquals(127.1, result.get(0).getLongitude(), 0.00001);
        assertEquals("서울 치과", result.get(1).getHospitalName());
    }
    @Test public void remoteFailureFallsBackToCatalog() {
        FakeSearch search = new FakeSearch();
        search.fail = true;
        assertEquals(2, run("서울", catalog, search).size());
    }
    @Test public void unconfiguredSearchAndEmptyQueryStayLocal() {
        FakeSearch search = new FakeSearch();
        search.configured = false;
        assertEquals(1, run("외과", catalog, search).size());
        search.configured = true;
        assertEquals(2, run("", catalog, search).size());
        assertEquals(0, search.calls);
    }
    private List<BookingHospitalOption> run(String query, BookingHospitalCatalog catalog, FakeSearch search) {
        AtomicReference<List<BookingHospitalOption>> result = new AtomicReference<>();
        coordinator.searchOptions(query, catalog, search, new RepositoryCallback<List<BookingHospitalOption>>() {
            @Override public void onSuccess(List<BookingHospitalOption> value) { result.set(value); }
            @Override public void onError(String message) { fail(message); }
        });
        return result.get();
    }
    private static final class FakeSearch implements BookingHospitalSelectorCoordinator.PlaceSearch {
        boolean configured = true;
        boolean fail;
        int calls;
        List<KakaoPlaceCoordinate> results = Collections.emptyList();
        @Override public boolean isConfigured() { return configured; }
        @Override public void search(String query, KakaoLocalPlaceSearchClient.KeywordCallback callback) {
            calls++;
            if (fail) callback.onError("합성 오류"); else callback.onSuccess(results);
        }
    }
}
