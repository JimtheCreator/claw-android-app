package viewmodels.alerts;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import backend.requests.CreatePatternAlertRequest;
import database.roomDB.AppDatabase;
import models.CachedSymbol;
import models.Pattern;
import models.PatternAlert;
import repositories.SymbolRepository;
import repositories.alerts.PatternAlertRepository;

public class PatternAlertViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 20;

    private final LiveData<List<Pattern>> allPatterns;
    private final MutableLiveData<String> patternSearchQuery = new MutableLiveData<>("");
    private final MutableLiveData<List<Pattern>> paginatedPatterns = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);

    private List<Pattern> currentFilteredPatterns = new ArrayList<>();
    private int currentPage = 0;
    private boolean hasMorePages = true;

    private final SymbolRepository symbolRepository;
    public final LiveData<Boolean> isSymbolSearchLoading;

    private final PatternAlertRepository patternAlertRepository;
    public final LiveData<Boolean> isLoading;
    public final LiveData<String> error;
    // --- ⬇️ START OF CHANGES ⬇️ ---

    // 1. REMOVE the old LiveData instance for alerts.
    // private LiveData<List<PatternAlert>> patternAlerts;

    // 2. ADD a trigger to request a refresh of the alerts list.
    private final MutableLiveData<String> alertRequestTrigger = new MutableLiveData<>();

    // 3. ADD the final, observable LiveData that the UI will listen to.
    private final LiveData<List<PatternAlert>> patternAlerts;


    public PatternAlertViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getInstance(application);
        allPatterns = database.patternDao().getAllPatterns();
        this.symbolRepository = new SymbolRepository(application);
        isSymbolSearchLoading = symbolRepository.isSymbolSearchLoading();
        // New
        this.patternAlertRepository = new PatternAlertRepository(application);
        this.isLoading = patternAlertRepository.isLoading;
        this.error = patternAlertRepository.error;

        // 4. ADD the switchMap transformation.
        // This links the trigger to the repository data source. When alertRequestTrigger changes,
        // it switches to a new LiveData from getPatternAlerts and pushes its value.
        patternAlerts = Transformations.switchMap(alertRequestTrigger, patternAlertRepository::getPatternAlerts);

        // Observe all patterns and handle filtering
        allPatterns.observeForever(patterns -> {
            if (patterns != null) {
                applyFilterAndPagination();
            }
        });

        // Observe search query changes
        patternSearchQuery.observeForever(query -> {
            resetPagination();
            applyFilterAndPagination();
        });

        patternSearchQuery.setValue("");
    }

    private void applyFilterAndPagination() {
        List<Pattern> allPatternsList = allPatterns.getValue();
        if (allPatternsList == null) return;

        String query = patternSearchQuery.getValue();

        // Apply filtering
        if (query == null || query.isEmpty()) {
            currentFilteredPatterns = new ArrayList<>(allPatternsList);
        } else {
            currentFilteredPatterns = allPatternsList.stream()
                    .filter(p -> p.displayName.toLowerCase().contains(query.toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Load first page
        loadPage(0);
    }

    private void loadPage(int page) {
        if (currentFilteredPatterns == null) return;

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, currentFilteredPatterns.size());

        if (startIndex >= currentFilteredPatterns.size()) {
            hasMorePages = false;
            isLoadingMore.setValue(false);
            return;
        }

        List<Pattern> pageData = currentFilteredPatterns.subList(startIndex, endIndex);

        if (page == 0) {
            // First page - replace all data
            paginatedPatterns.setValue(new ArrayList<>(pageData));
        } else {
            // Subsequent pages - append data
            List<Pattern> currentData = paginatedPatterns.getValue();
            if (currentData != null) {
                List<Pattern> updatedData = new ArrayList<>(currentData);
                updatedData.addAll(pageData);
                paginatedPatterns.setValue(updatedData);
            }
        }

        hasMorePages = endIndex < currentFilteredPatterns.size();
        isLoadingMore.setValue(false);
    }

    public void loadMorePatterns() {
        if (isLoadingMore.getValue() == Boolean.TRUE || !hasMorePages) {
            return;
        }

        isLoadingMore.setValue(true);
        currentPage++;
        loadPage(currentPage);
    }

    private void resetPagination() {
        currentPage = 0;
        hasMorePages = true;
        isLoadingMore.setValue(false);
    }

    public LiveData<List<Pattern>> getAllPatterns() {
        return allPatterns;
    }


    public LiveData<List<Pattern>> getPaginatedPatterns() {
        return paginatedPatterns;
    }

    public LiveData<Boolean> isLoadingMore() {
        return isLoadingMore;
    }

    public boolean hasMorePages() {
        return hasMorePages;
    }

    public void searchPatterns(String query) {
        patternSearchQuery.setValue(query != null ? query : "");
    }

    public LiveData<List<CachedSymbol>> searchSymbols(String query) {
        return symbolRepository.searchSymbols(query);
    }

    // 5. CHANGE the getter to return the stable, transformed LiveData. No parameter is needed.
    public LiveData<List<PatternAlert>> getPatternAlerts() {
        return patternAlerts;
    }

    public LiveData<PatternAlert> createPatternAlert(String userID, String symbol, String patternName, String timeInterval, String patternState, String notification_method) {
        CreatePatternAlertRequest request = new CreatePatternAlertRequest(symbol, patternName, timeInterval, patternState, notification_method);
        return patternAlertRepository.createPatternAlert(request, userID);
    }

    public LiveData<Boolean> deletePatternAlert(String alertId, String userID) {
        return patternAlertRepository.deletePatternAlert(alertId, userID);
    }

    // 6. CHANGE refreshAlerts to simply update the trigger's value.
    public void refreshAlerts(String userID) {
        if (userID != null) {
            alertRequestTrigger.setValue(userID);
        }
    }
}