package backend;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import viewmodels.telegram_login.TelegramAuthViewModel.TelegramAuthResponse;

public interface TelegramAuthService {
    @POST("telegram")
    Call<TelegramAuthResponse> verifyTelegramAuth(@Body Map<String, String> authData);
}
