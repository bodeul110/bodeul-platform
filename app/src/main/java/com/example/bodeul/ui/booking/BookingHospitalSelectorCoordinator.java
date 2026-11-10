package com.example.bodeul.ui.booking;

import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.BookingHospitalOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.data.map.KakaoPlaceCoordinate;

/**
 * 병원 선택 화면에 필요한 저장소 조회와 화면용 목록 조합을 맡는다.
 */
public final class BookingHospitalSelectorCoordinator {
    public interface PlaceSearch {
        boolean isConfigured();
        void search(String query, KakaoLocalPlaceSearchClient.KeywordCallback callback);
    }
    private final BookingRepository bookingRepository;

    public BookingHospitalSelectorCoordinator(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public void loadCatalog(RepositoryCallback<BookingHospitalCatalog> callback) {
        bookingRepository.getHospitalOptions(new RepositoryCallback<List<BookingHospitalOption>>() {
            @Override
            public void onSuccess(List<BookingHospitalOption> result) {
                List<BookingHospitalOption> safeResult = result == null ? Collections.emptyList() : result;
                callback.onSuccess(new BookingHospitalCatalog(safeResult));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void searchOptions(
            String query,
            BookingHospitalCatalog catalog,
            PlaceSearch kakaoClient,
            RepositoryCallback<List<BookingHospitalOption>> callback
    ) {
        if (query == null || query.trim().isEmpty()) {
            callback.onSuccess(catalog.filter(query));
            return;
        }

        List<BookingHospitalOption> localResults = catalog.filter(query);

        if (!kakaoClient.isConfigured()) {
            callback.onSuccess(localResults);
            return;
        }

        kakaoClient.search(query, new KakaoLocalPlaceSearchClient.KeywordCallback() {
            @Override
            public void onSuccess(List<KakaoPlaceCoordinate> results) {
                if (results == null || results.isEmpty()) {
                    callback.onSuccess(localResults);
                    return;
                }

                List<BookingHospitalOption> combined = new ArrayList<>();
                Set<String> seen = new HashSet<>();

                for (KakaoPlaceCoordinate kakao : results) {
                    String name = kakao.getName();
                    seen.add(name);

                    List<BookingHospitalOption> matchingLocal = catalog.filter(name);
                    List<String> departments = Collections.emptyList();
                    for (BookingHospitalOption local : matchingLocal) {
                        if (local.getHospitalName().equals(name)) {
                            departments = local.getDepartmentNames();
                            break;
                        }
                    }

                    combined.add(new BookingHospitalOption(name, departments, kakao.getLatitude(), kakao.getLongitude()));
                }

                for (BookingHospitalOption local : localResults) {
                    if (!seen.contains(local.getHospitalName())) {
                        combined.add(local);
                    }
                }

                callback.onSuccess(combined);
            }

            @Override
            public void onError(String message) {
                callback.onSuccess(localResults);
            }
        });
    }
}
