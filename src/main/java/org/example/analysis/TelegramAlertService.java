package org.example.analysis;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

public class TelegramAlertService {

    private static final String BOT_TOKEN = "8520143920:AAECmxc2IbE8Excjd48gMnCYBf6gf1Ef_cg";
    private static final String CHAT_ID = "1852301057";
    private static final RestTemplate restTemplate = new RestTemplate();


    public static void send(String message) {
        try {
            String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", CHAT_ID);
            body.put("text", message);   // 🔥 NO URL encoding here
            body.put("parse_mode", "HTML"); // optional

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            restTemplate.postForObject(url, request, String.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

