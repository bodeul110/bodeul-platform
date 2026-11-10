package com.example.bodeul.ui.booking;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.bodeul.R;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.data.map.KakaoLocalPlaceSearchClient;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.util.StatePanelHelper;
import java.util.List;

/** 예약 저장 계약은 유지하고 검색·선택 화면의 수명주기만 연결한다. */
public class BookingHospitalSelectorActivity extends AppCompatActivity {
    private static final String EXTRA_INITIAL_HOSPITAL = "initialHospital";
    private static final String EXTRA_INITIAL_DEPARTMENT = "initialDepartment";
    private static final String EXTRA_HOSPITAL_LATITUDE = "hospitalLatitude";
    private static final String EXTRA_HOSPITAL_LONGITUDE = "hospitalLongitude";
    private static final String STATE_QUERY = "hospitalQuery";
    private BookingHospitalSelectorCoordinator coordinator;
    private BookingHospitalSelectorCoordinator.PlaceSearch placeSearch;
    private BookingHospitalOptionAdapter adapter;
    private BookingHospitalCatalog catalog = BookingHospitalCatalog.empty();
    private EditText searchInput;
    private View progress;
    private View empty;
    private View statePanel;
    private View error;
    private ListView list;
    private boolean loadingCatalog;
    private boolean catalogFailed;
    private int requestVersion;
    private int catalogVersion;

    public static Intent createIntent(Context context, BookingHospitalSelection selection) {
        Intent intent = new Intent(context, BookingHospitalSelectorActivity.class);
        putSelection(intent, selection);
        return intent;
    }

    public static BookingHospitalSelection parseResult(@Nullable Intent data) {
        if (data == null) return new BookingHospitalSelection("", "");
        return new BookingHospitalSelection(data.getStringExtra(EXTRA_INITIAL_HOSPITAL),
                data.getStringExtra(EXTRA_INITIAL_DEPARTMENT),
                data.getDoubleExtra(EXTRA_HOSPITAL_LATITUDE, 0),
                data.getDoubleExtra(EXTRA_HOSPITAL_LONGITUDE, 0));
    }

    private static void putSelection(Intent intent, BookingHospitalSelection selection) {
        intent.putExtra(EXTRA_INITIAL_HOSPITAL, selection.getHospitalName());
        intent.putExtra(EXTRA_INITIAL_DEPARTMENT, selection.getDepartmentName());
        intent.putExtra(EXTRA_HOSPITAL_LATITUDE, selection.getHospitalLatitude());
        intent.putExtra(EXTRA_HOSPITAL_LONGITUDE, selection.getHospitalLongitude());
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_Bodeul_FinalBooking);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_hospital_selector);
        configureSystemBars();
        coordinator = new BookingHospitalSelectorCoordinator(provideBookingRepository());
        placeSearch = providePlaceSearch();
        searchInput = findViewById(R.id.searchBookingHospital);
        progress = findViewById(R.id.progressBookingHospitalSelector);
        empty = findViewById(R.id.bookingHospitalEmpty);
        statePanel = findViewById(R.id.bookingHospitalSelectorStatePanel);
        error = findViewById(R.id.bookingHospitalError);
        list = findViewById(R.id.listBookingHospitalOptions);
        adapter = new BookingHospitalOptionAdapter(this, this::openDepartmentSelector, this::openMap);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openDepartmentSelector(adapter.getItem(position)));
        findViewById(R.id.buttonBackBookingHospitalSelector).setOnClickListener(v -> finish());
        findViewById(R.id.buttonClearBookingHospital).setOnClickListener(v -> searchInput.setText(""));
        findViewById(R.id.buttonBookingHospitalOptions).setOnClickListener(this::showRegionMenu);
        findViewById(R.id.buttonBookingHospitalManualInput).setOnClickListener(v -> {
            hideKeyboard();
            if (getSupportFragmentManager().findFragmentByTag(BookingHospitalManualDialog.TAG) == null) {
                BookingHospitalSelection initial = parseResult(getIntent());
                BookingHospitalManualDialog.create(query().trim().equals(initial.getHospitalName())
                                ? initial : new BookingHospitalSelection(query(), ""), true)
                        .show(getSupportFragmentManager(), BookingHospitalManualDialog.TAG);
            }
        });
        getSupportFragmentManager().setFragmentResultListener(BookingDepartmentSheet.RESULT, this,
                (key, result) -> finishWithSelection(BookingDepartmentSheet.readSelection(result)));

        searchInput.setText(savedInstanceState == null
                ? getIntent().getStringExtra(EXTRA_INITIAL_HOSPITAL) : savedInstanceState.getString(STATE_QUERY, ""));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!loadingCatalog) search();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            hideKeyboard();
            if (!loadingCatalog) search();
            return true;
        });
        loadCatalog();
    }

    protected BookingRepository provideBookingRepository() {
        return ServiceLocator.provideBookingRepository(this);
    }

    protected BookingHospitalSelectorCoordinator.PlaceSearch providePlaceSearch() {
        KakaoLocalPlaceSearchClient client = new KakaoLocalPlaceSearchClient(this);
        return new BookingHospitalSelectorCoordinator.PlaceSearch() {
            @Override public boolean isConfigured() { return client.isConfigured(); }
            @Override public void search(String query, KakaoLocalPlaceSearchClient.KeywordCallback callback) {
                client.searchKeyword(query, callback);
            }
        };
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_QUERY, query());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onDestroy() {
        requestVersion++;
        catalogVersion++;
        super.onDestroy();
    }

    private void configureSystemBars() {
        View root = findViewById(R.id.bookingHospitalSelectorRoot);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(true);
        ViewCompat.requestApplyInsets(root);
    }

    private void hideKeyboard() {
        searchInput.clearFocus();
        WindowCompat.getInsetsController(getWindow(), searchInput).hide(WindowInsetsCompat.Type.ime());
    }

    private String query() { return searchInput.getText().toString(); }

    private void showRegionMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        int[] labels = {R.string.booking_hospital_selector_region_all,
                R.string.booking_hospital_selector_region_jongno, R.string.booking_hospital_selector_region_gangnam};
        int[] queries = {R.string.booking_hospital_selector_region_all_query,
                R.string.booking_hospital_selector_region_jongno_query, R.string.booking_hospital_selector_region_gangnam_query};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]);
        if (catalogFailed) menu.getMenu().add(0, labels.length, labels.length, R.string.state_action_retry);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == labels.length) loadCatalog();
            else searchInput.setText(queries[item.getItemId()]);
            hideKeyboard();
            return true;
        });
        menu.show();
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private void loadCatalog() {
        int version = ++catalogVersion;
        requestVersion++;
        loadingCatalog = true;
        catalogFailed = false;
        renderLoading();
        StatePanelHelper.hide(statePanel);
        coordinator.loadCatalog(new RepositoryCallback<BookingHospitalCatalog>() {
            @Override public void onSuccess(BookingHospitalCatalog result) {
                if (!alive() || version != catalogVersion) return;
                catalog = result;
                loadingCatalog = false;
                search();
            }
            @Override public void onError(String message) {
                if (!alive() || version != catalogVersion) return;
                loadingCatalog = false;
                catalogFailed = true;
                StatePanelHelper.show(statePanel, StatePanelHelper.Tone.ERROR,
                        getString(R.string.state_badge_error), getString(R.string.booking_hospital_selector_error_title),
                        getString(R.string.state_load_error_body), getString(R.string.state_action_retry),
                        v -> loadCatalog(), null, null);
                // 가이드 목록 오류가 있어도 외부 병원 검색과 직접 입력은 계속 사용할 수 있다.
                search();
            }
        });
    }

    private void renderLoading() {
        list.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
    }

    private void search() {
        int version = ++requestVersion;
        renderLoading();
        coordinator.searchOptions(query().trim(), catalog, placeSearch, new RepositoryCallback<List<BookingHospitalOption>>() {
            @Override public void onSuccess(List<BookingHospitalOption> result) {
                if (!alive() || version != requestVersion) return;
                progress.setVisibility(View.GONE);
                adapter.submitList(result);
                list.setContentDescription(getString(R.string.booking_hospital_selector_result_count, result.size()));
                list.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
                boolean showCatalogError = result.isEmpty() && catalogFailed && query().trim().isEmpty();
                error.setVisibility(showCatalogError ? View.VISIBLE : View.GONE);
                empty.setVisibility(result.isEmpty() && !showCatalogError ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(String message) { /* 검색은 가이드 목록으로 대체한다. */ }
        });
    }

    private void openDepartmentSelector(BookingHospitalOption option) {
        hideKeyboard();
        if (getSupportFragmentManager().findFragmentByTag(BookingDepartmentSheet.TAG) != null) return;
        BookingHospitalSelection initial = parseResult(getIntent());
        String department = option.getHospitalName().equals(initial.getHospitalName()) ? initial.getDepartmentName() : "";
        BookingDepartmentSheet.create(option, department).show(getSupportFragmentManager(), BookingDepartmentSheet.TAG);
    }

    protected void finishWithSelection(BookingHospitalSelection selection) {
        Intent result = new Intent();
        putSelection(result, selection);
        setResult(RESULT_OK, result);
        finish();
    }

    protected void openMap(BookingHospitalOption option) {
        String query = option.getHospitalName();
        if (option.getLatitude() != 0 && option.getLongitude() != 0) {
            query = option.getLatitude() + "," + option.getLongitude() + "(" + query + ")";
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.final_hospital_map_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}
