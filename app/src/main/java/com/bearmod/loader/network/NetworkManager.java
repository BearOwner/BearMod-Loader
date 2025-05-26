package com.bearmod.loader.network;

import android.content.Context;
import android.util.Log;

import com.bearmod.loader.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Network manager
 * Handles secure API interactions with certificate pinning
 */
public class NetworkManager {
    
    private static final String TAG = "NetworkManager";
    
    private static NetworkManager instance;
    private final OkHttpClient okHttpClient;
    private final Retrofit retrofit;
    
    // API endpoints
    private static final String KEYAUTH_API_URL = "https://keyauth.win/api/1.2/";
    private static final String BEARMOD_API_URL = "https://api.bearmod.com/";
    private static final String GITHUB_API_URL = "https://api.github.com/";
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private NetworkManager(Context context) {
        // Create OkHttpClient with certificate pinning
        okHttpClient = createSecureOkHttpClient();
        
        // Create Retrofit instance
        retrofit = new Retrofit.Builder()
                .baseUrl(BEARMOD_API_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
    
    /**
     * Get NetworkManager instance
     * @param context Application context
     * @return NetworkManager instance
     */
    public static synchronized NetworkManager getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * Create secure OkHttpClient with certificate pinning
     * @return OkHttpClient instance
     */
    private OkHttpClient createSecureOkHttpClient() {
        try {
            // Create certificate pinner
            CertificatePinner certificatePinner = new CertificatePinner.Builder()
                    // KeyAuth certificate pinning
                    .add("keyauth.win", "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtLMkBgFF2Fuihg=")
                    // GitHub certificate pinning
                    .add("api.github.com", "sha256/ORtIOYkm5k6Nf2tgAK/uwftKfNhJB3QS0Hs608SiRmE=")
                    .add("github.com", "sha256/ORtIOYkm5k6Nf2tgAK/uwftKfNhJB3QS0Hs608SiRmE=")
                    // BearMod certificate pinning (placeholder - replace with actual certificate)
                    .add("api.bearmod.com", "sha256/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX=")
                    .build();
            
            // Create logging interceptor for debug builds
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(BuildConfig.DEBUG ? 
                    HttpLoggingInterceptor.Level.BODY : HttpLoggingInterceptor.Level.NONE);
            
            // Build OkHttpClient
            return new OkHttpClient.Builder()
                    .certificatePinner(certificatePinner)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(loggingInterceptor)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating secure OkHttpClient: " + e.getMessage());
            
            // Fallback to non-pinned client
            return new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
    }
    
    /**
     * Get OkHttpClient instance
     * @return OkHttpClient instance
     */
    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }
    
    /**
     * Get Retrofit instance for BearMod API
     * @return Retrofit instance
     */
    public Retrofit getRetrofit() {
        return retrofit;
    }
    
    /**
     * Get Retrofit instance for KeyAuth API
     * @return Retrofit instance
     */
    public Retrofit getKeyAuthRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(KEYAUTH_API_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
    
    /**
     * Get Retrofit instance for GitHub API
     * @return Retrofit instance
     */
    public Retrofit getGitHubRetrofit() {
        return new Retrofit.Builder()
                .baseUrl(GITHUB_API_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
    
    /**
     * Create API service
     * @param serviceClass Service class
     * @param <T> Service type
     * @return API service
     */
    public <T> T createService(Class<T> serviceClass) {
        return retrofit.create(serviceClass);
    }
    
    /**
     * Create KeyAuth API service
     * @param serviceClass Service class
     * @param <T> Service type
     * @return KeyAuth API service
     */
    public <T> T createKeyAuthService(Class<T> serviceClass) {
        return getKeyAuthRetrofit().create(serviceClass);
    }
    
    /**
     * Create GitHub API service
     * @param serviceClass Service class
     * @param <T> Service type
     * @return GitHub API service
     */
    public <T> T createGitHubService(Class<T> serviceClass) {
        return getGitHubRetrofit().create(serviceClass);
    }
}
