package edu.help.config;

public class ApiConfig {
    public static final String ENV = System.getenv("API_ENV");
    public static final String BASE_HTTP_PATH = "/redis-" + ENV + "-http";
    public static final String BASE_WS_PATH = "/redis-" + ENV + "-ws";
    public static final String FULL_HTTP_PATH = "https://www.megrim.com/postgres-" + ENV + "-http";
}
