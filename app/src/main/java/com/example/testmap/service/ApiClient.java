package com.example.testmap.service;

// ApiClient.java
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    //private static final String BASE_URL = "http://testhopon.p-e.kr:8080/";
    //private static final String BASE_URL = "http://10.0.2.2:8080/";
    private static final String BASE_URL = "http://168.138.168.66:8080/";
    private static ApiService INSTANCE;

    public static ApiService get() {
        if (INSTANCE == null) {
            INSTANCE = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return INSTANCE;
    }
}
