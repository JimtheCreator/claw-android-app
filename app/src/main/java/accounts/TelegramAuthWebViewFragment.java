package accounts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.claw.ai.R;
import com.claw.ai.databinding.FragmentTelegramAuthWebViewBinding;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import viewmodels.telegram_login.TelegramAuthViewModel;

public class TelegramAuthWebViewFragment extends BottomSheetDialogFragment {

    private static final String TAG = "TelegramAuthWebView";
    private FragmentTelegramAuthWebViewBinding binding;
    private TelegramAuthViewModel telegramAuthViewModel;

    public static TelegramAuthWebViewFragment newInstance() {
        Bundle args = new Bundle();
        TelegramAuthWebViewFragment fragment = new TelegramAuthWebViewFragment();
        fragment.setArguments(args);
        return fragment;
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentTelegramAuthWebViewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        telegramAuthViewModel = new ViewModelProvider(requireActivity()).get(TelegramAuthViewModel.class);
        setupObservers();
        String botID = getString(R.string.telegram_bot_id);
        binding.closeButton.setOnClickListener(v -> {
            dismiss();
            showLoading(false);
        });

        // Configure WebView
        WebView webView = binding.telegramWebView;
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        // Add JavaScript Interface to receive auth data
        webView.addJavascriptInterface(new TelegramAuthJsInterface(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String returnUrl = getString(R.string.telegram_auth_return_url);

                // Intercept the return_to URL
                if (url.startsWith(returnUrl)) {
                    Log.d(TAG, "Intercepted return_to URL - closing fragment");
                    dismiss(); // Close the WebView fragment
                    return true; // Block the URL from loading
                }

                return false; // Allow other URLs to load
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                showLoading(false);

                // Manually trigger Telegram auth flow
                webView.evaluateJavascript(
                        "(function() { " +
                                "window.Telegram.Login.auth({ bot_id: '" + botID + "', request_access: true }, function(data) { " +
                                "Android.onTelegramAuth(JSON.stringify(data)); " +
                                "});" +
                                "})();",
                        null
                );
            }
        });



        // Show loading UI
        showLoading(true);

        // Load Telegram login widget
        String telegramAuthUrl = "https://oauth.telegram.org/auth?bot_id=" + botID +
                "&origin=" + getString(R.string.telegram_auth_origin) +
                "&return_to=" + getString(R.string.telegram_auth_return_url);

        webView.loadUrl(telegramAuthUrl);
    }

    private void setupObservers() {
        telegramAuthViewModel.getAuthState().observe(getViewLifecycleOwner(), authState -> {
            switch (authState) {
                case LOADING:
                    showLoading(true);
                    break;
                case AUTHENTICATED:
                    showLoading(false);
                    dismiss(); // Close this fragment when authenticated
                    break;
                case ERROR:
                    showLoading(false);
                    // Error message is handled by its own observer
                    break;
                case UNAUTHENTICATED:
                    showLoading(false);
                    break;
            }
        });

        telegramAuthViewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                binding.errorText.setText(errorMessage);
                binding.errorText.setVisibility(View.VISIBLE);
            } else {
                binding.errorText.setVisibility(View.GONE);
            }
        });
    }

    private void showLoading(boolean isLoading) {
        if (binding == null) return; // View could be destroyed
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.telegramWebView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    /**
     * JavaScript interface to receive Telegram authentication data
     */
    private class TelegramAuthJsInterface {
        @JavascriptInterface
        public void onTelegramAuth(String authData) {
            Log.d(TAG, "Raw auth data: " + authData); // <-- ADD THIS
            try {
                JSONObject data = new JSONObject(authData);
                requireActivity().runOnUiThread(() -> telegramAuthViewModel.processTelegramAuthData(data.toString()));
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing Telegram auth data", e);
                requireActivity().runOnUiThread(() ->
                        telegramAuthViewModel.setErrorMessage("Failed to process Telegram authentication data"));
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Ensures the bottom sheet is expanded
        View view = getView();
        if (view != null) {
            View parent = (View) view.getParent();
            if (parent != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true); // Optional: prevents it from being collapsed

                ViewGroup.LayoutParams layoutParams = parent.getLayoutParams();
                layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                parent.setLayoutParams(layoutParams);
            }

        }
    }
}
