package main.java.com.fabflix.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import main.java.com.fabflix.util.ConfigManager;
import main.java.com.fabflix.util.DatabaseUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.jasypt.util.password.StrongPasswordEncryptor;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String recaptchaToken = request.getParameter("g-recaptcha-response");

        response.setContentType("application/json");
        Map<String, Object> responseData = new HashMap<>();

        try {
             //验证reCAPTCHA
            if (!verifyRecaptcha(recaptchaToken)) {
                responseData.put("status", "error");
                responseData.put("message", "reCAPTCHA verification failed");
                response.getWriter().write(new com.google.gson.Gson().toJson(responseData));
                return;
            }

            // 验证用户名和密码
            Connection conn = DatabaseUtil.getConnection();
            // 修改查询，只获取email和加密的密码
            String query = "SELECT id, password FROM customers WHERE email = ?";

            try (PreparedStatement statement = conn.prepareStatement(query)) {
                statement.setString(1, email);

                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    String encryptedPassword = rs.getString("password");
                    // 使用PasswordUtil验证密码
//                    PasswordUtil.checkPassword(password, encryptedPassword)
                    if (new StrongPasswordEncryptor().checkPassword(password, encryptedPassword)) {
                        // 登录成功
                        HttpSession session = request.getSession();
                        session.setAttribute("user", email);
                        session.setAttribute("customerId", rs.getString("id"));

                        responseData.put("status", "success");
                    } else {
                        responseData.put("status", "error");
                        responseData.put("message", "Invalid email or password");
                    }
                } else {
                    responseData.put("status", "error");
                    responseData.put("message", "Invalid email or password");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            responseData.put("status", "error");
            responseData.put("message", "Server error: " + e.getMessage());
        }

        response.getWriter().write(new com.google.gson.Gson().toJson(responseData));
    }

    private boolean verifyRecaptcha(String token) {
        if (token == null || token.isEmpty()) {
            return false;
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
                return recaptchaResponse.get("success").getAsBoolean();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
