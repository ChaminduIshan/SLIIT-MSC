import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.concurrent.CountDownLatch;

import com.sun.net.httpserver.HttpServer;

public class GoogleOAuthExample {

    static final String clientId = "YOUR_GOOGLE_CLIENT_ID";
    static final String clientSecret = "YOUR_GOOGLE_CLIENT_SECRET";
    static final String redirectUri = "http://localhost:8080/callback";

    static final OkHttpClient client = new OkHttpClient();

    public static void main(String[] args) throws Exception {
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=openid%20email%20profile";

        System.out.println("Opening browser for login...");
        Desktop.getDesktop().browse(URI.create(authUrl));

        CountDownLatch latch = new CountDownLatch(1);

        // Simple HTTP server to catch the callback
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        final String[] codeHolder = new String[1];

        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("code=")) {
                    codeHolder[0] = URLDecoder.decode(param.substring(5), "UTF-8");
                    break;
                }
            }
            String response = "You can now return to the Java console.";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
            latch.countDown();
        });
        server.start();

        latch.await();
        server.stop(0);

        String code = codeHolder[0];
        System.out.println("Received code: " + code);

        // Exchange code for token
        RequestBody tokenRequestBody = new FormBody.Builder()
                .add("code", code)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .build();

        Request tokenRequest = new Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(tokenRequestBody)
                .build();

        try (Response response = client.newCall(tokenRequest).execute()) {
            String json = response.body().string();
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            String accessToken = jsonObject.get("access_token").getAsString();
            System.out.println("Access token: " + accessToken);

            // Call userinfo endpoint
            Request userInfoRequest = new Request.Builder()
                    .url("https://openidconnect.googleapis.com/v1/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response userInfoResponse = client.newCall(userInfoRequest).execute()) {
                System.out.println("User Info: " + userInfoResponse.body().string());
            }
        }
    }
}
