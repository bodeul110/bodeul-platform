package com.example.bodeul.ui.booking;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;

/**
 * 예약에 저장될 실제 병원 좌표만 지도에 표시한다.
 */
final class BookingHospitalMapPreviewController {
    private static final String TAG = "BookingHospitalMap";

    private final MapView mapView;
    private final View placeholder;
    private final TextView placeholderTitle;
    private final TextView placeholderBody;
    private BookingHospitalSelection currentSelection =
            new BookingHospitalSelection("", "", 0.0, 0.0);
    private KakaoMap kakaoMap;
    private Label hospitalMarker;
    private boolean mapFailed;
    private boolean mapStarted;
    private boolean hostResumed;
    private boolean hostDestroyed;
    private final Runnable startMapRunnable = this::startMapIfNeeded;

    BookingHospitalMapPreviewController(
            MapView mapView,
            View placeholder,
            TextView placeholderTitle,
            TextView placeholderBody
    ) {
        this.mapView = mapView;
        this.placeholder = placeholder;
        this.placeholderTitle = placeholderTitle;
        this.placeholderBody = placeholderBody;
        mapView.setVisibility(View.INVISIBLE);
        renderPlaceholder();
    }

    void bindSelection(@NonNull BookingHospitalSelection selection) {
        currentSelection = selection;
        render();
    }

    void onResume() {
        if (hostDestroyed) {
            return;
        }
        hostResumed = true;
        if (mapStarted) {
            mapView.resume();
        }
    }

    void onPause() {
        hostResumed = false;
        if (mapStarted) {
            mapView.pause();
        }
    }

    void onHostContentVisible() {
        if (!hostDestroyed) {
            mapView.post(startMapRunnable);
        }
    }

    void onDestroy() {
        hostDestroyed = true;
        hostResumed = false;
        mapView.removeCallbacks(startMapRunnable);
        if (mapStarted) {
            mapView.pause();
        }
        kakaoMap = null;
        hospitalMarker = null;
    }

    private void startMapIfNeeded() {
        if (hostDestroyed) {
            return;
        }
        if (mapStarted) {
            render();
            return;
        }
        mapStarted = true;
        mapView.start(new MapLifeCycleCallback() {
            @Override
            public void onMapDestroy() {
                kakaoMap = null;
                hospitalMarker = null;
            }

            @Override
            public void onMapError(Exception exception) {
                if (hostDestroyed) {
                    return;
                }
                Log.w(TAG, "병원 지도 미리보기 초기화 실패", exception);
                mapFailed = true;
                render();
            }
        }, new KakaoMapReadyCallback() {
            @Override
            public void onMapReady(KakaoMap map) {
                if (hostDestroyed) {
                    return;
                }
                kakaoMap = map;
                render();
            }
        });
        if (hostResumed) {
            mapView.resume();
        }
    }

    private void render() {
        if (!currentSelection.isComplete()) {
            removeMarker();
            mapView.setVisibility(View.INVISIBLE);
            renderPlaceholder();
            return;
        }
        if (!hasCoordinates(currentSelection)) {
            removeMarker();
            mapView.setVisibility(View.INVISIBLE);
            placeholder.setVisibility(View.VISIBLE);
            placeholderTitle.setText(currentSelection.getHospitalName());
            placeholderBody.setText(R.string.booking_main_map_manual_body);
            return;
        }
        if (mapFailed) {
            mapView.setVisibility(View.INVISIBLE);
            placeholder.setVisibility(View.VISIBLE);
            placeholderTitle.setText(currentSelection.getHospitalName());
            placeholderBody.setText(R.string.booking_main_map_error_body);
            return;
        }
        if (kakaoMap == null) {
            mapView.setVisibility(View.INVISIBLE);
            placeholder.setVisibility(View.VISIBLE);
            placeholderTitle.setText(currentSelection.getHospitalName());
            placeholderBody.setText(R.string.booking_main_map_loading_body);
            return;
        }

        LatLng position = LatLng.from(
                currentSelection.getHospitalLatitude(),
                currentSelection.getHospitalLongitude()
        );
        if (hospitalMarker == null) {
            LabelOptions options = LabelOptions.from(position);
            options.setStyles(LabelStyle.from(R.drawable.ic_map_marker_hospital));
            hospitalMarker = kakaoMap.getLabelManager().getLayer().addLabel(options);
        } else {
            hospitalMarker.moveTo(position);
        }
        kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(position));
        placeholder.setVisibility(View.GONE);
        mapView.setVisibility(View.VISIBLE);
    }

    private void renderPlaceholder() {
        placeholder.setVisibility(View.VISIBLE);
        placeholderTitle.setText(R.string.booking_main_map_empty_title);
        placeholderBody.setText(R.string.booking_main_map_empty_body);
    }

    private void removeMarker() {
        if (hospitalMarker != null) {
            hospitalMarker.remove();
            hospitalMarker = null;
        }
    }

    private boolean hasCoordinates(BookingHospitalSelection selection) {
        return selection.getHospitalLatitude() != 0.0
                && selection.getHospitalLongitude() != 0.0;
    }
}
