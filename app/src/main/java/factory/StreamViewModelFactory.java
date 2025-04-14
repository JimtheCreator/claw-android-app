package factory;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import repositories.StreamRepository;
import viewmodels.StreamViewModel;

public class StreamViewModelFactory implements ViewModelProvider.Factory {
    private final StreamRepository repository;

    public StreamViewModelFactory(StreamRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    @Override
    public <T extends ViewModel> T create(Class<T> modelClass) {
        if (modelClass.isAssignableFrom(StreamViewModel.class)) {
            return (T) new StreamViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
