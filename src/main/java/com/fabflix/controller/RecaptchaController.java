package main.java.com.fabflix.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import main.java.com.fabflix.util.ConfigManager;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import java.io.IOException;
import java.util.Arrays;

@WebServlet(name = "RecaptchaController", urlPatterns = {"/verify-recaptcha"})
public class RecaptchaController extends HttpServlet {


@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String token = request.getParameter("token");
    JsonObject jsonObject = new JsonObject();

    if (token == null || token.isEmpty()) {
        jsonObject.addProperty("success", false);
        jsonObject.addProperty("error", "Token is missing");
        response.getWriter().write(jsonObject.toString());
        return;
    }

    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        HttpPost httpPost = new HttpPost("https://www.google.com/recaptcha/api/siteverify");
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
        httpPost.setEntity(new UrlEncodedFormEntity(
                Arrays.asList(
                        new BasicNameValuePair("secret", ConfigManager.getRecaptchaSecretKey()),
                        new BasicNameValuePair("response", token)
                )));

        try (CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(httpResponse.getEntity());
            JsonObject recaptchaResponse = JsonParser.parseString(responseBody).getAsJsonObject();

            if (recaptchaResponse.get("success").getAsBoolean()) {
                jsonObject.addProperty("success", true);
            } else {
                jsonObject.addProperty("success", false);
                jsonObject.addProperty("error", recaptchaResponse.get("error-codes").getAsJsonArray().get(0).getAsString());
            }
        }
    } catch (Exception e) {
        jsonObject.addProperty("success", false);
        jsonObject.addProperty("error", e.getMessage());
    }

    response.setContentType("application/json");
    response.getWriter().write(jsonObject.toString());
}


}
