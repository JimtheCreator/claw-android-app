package fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.claw.ai.databinding.FragmentMoreTabBinding;

public class MoreTabFragment extends Fragment {

    FragmentMoreTabBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        // Initialize view binding for fragment
        binding = FragmentMoreTabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize your views and set up listeners here
        // Example: binding.textViewTitle.setText("Home");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up view binding
        binding = null;
    }
}