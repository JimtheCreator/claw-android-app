package fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.claw.ai.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link AlertTabFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AlertTabFragment extends Fragment {


    public AlertTabFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_alert_tab, container, false);
    }
}