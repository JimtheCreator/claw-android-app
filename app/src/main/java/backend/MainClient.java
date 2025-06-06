package backend;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainClient {
    private static Retrofit retrofit;

    public static Retrofit getInstance() {
        if (retrofit == null) {
            // Configure logging
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);  // Log full request/response

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl("https://stable-wholly-crappie.ngrok-free.app/api/v1/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build();
        }

        return retrofit;
    }

    public static ApiService getApiService() {
        return getInstance().create(ApiService.class);
    }
}

