package factory;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import viewmodels.HomeViewModel;

public class HomeViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;

    public HomeViewModelFactory(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
