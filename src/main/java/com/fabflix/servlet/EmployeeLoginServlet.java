package main.java.com.fabflix.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import main.java.com.fabflix.util.DatabaseUtil;
import main.java.com.fabflix.util.PasswordUtil;
import main.java.com.fabflix.util.ConfigManager;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicNameValuePair;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EmployeeLoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        Map<String, Object> responseData = new HashMap<>();

        // 读取JSON请求体
        BufferedReader reader = request.getReader();
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }
        String jsonData = jsonBuilder.toString();
        System.out.println("Received login request: " + jsonData);  // 添加日志

        // 解析JSON数据
        Map<String, Object> requestData = new com.google.gson.Gson().fromJson(jsonData, Map.class);
        String email = (String) requestData.get("email");
        String password = (String) requestData.get("password");
        String recaptchaToken = (String) requestData.get("g-recaptcha-response");

        if (!verifyRecaptcha(recaptchaToken)) {
            responseData.put("status", "error");
            responseData.put("message", "reCAPTCHA verification failed");
            response.getWriter().write(new com.google.gson.Gson().toJson(responseData));
            return;
        }
        System.out.println("Attempting login for email: " + email);  // 添加日志
        System.out.println("Plain password (debug): " + password); // debug log (plain password)

        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT password, fullname FROM employees WHERE email = ?";
            System.out.println("Executing query: " + query);  // 添加日志

            try (PreparedStatement statement = conn.prepareStatement(query)) {
                statement.setString(1, email);

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String encryptedPassword = rs.getString("password");
                        System.out.println("Found user, encrypted password (debug): " + encryptedPassword);  // debug log (encrypted password)
                        
                        boolean passwordMatch = PasswordUtil.checkPassword(password, encryptedPassword);
                        System.out.println("Password match: " + passwordMatch);  // 添加日志

                        if (passwordMatch) {
                            // 登录成功
                            HttpSession session = request.getSession();
                            session.setAttribute("employee", email);
                            session.setAttribute("employeeName", rs.getString("fullname"));

                            responseData.put("status", "success");
                            System.out.println("Login successful for: " + email);  // 添加日志
                        } else {
                            responseData.put("status", "error");
                            responseData.put("message", "Invalid email or password");
                            System.out.println("Password mismatch for: " + email);  // 添加日志
                        }
                    } else {
                        responseData.put("status", "error");
                        responseData.put("message", "Invalid email or password");
                        System.out.println("User not found: " + email);  // 添加日志
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Login error: " + e.getMessage());  // 添加错误日志
            e.printStackTrace(System.err);  // 打印完整堆栈
            responseData.put("status", "error");
            responseData.put("message", "Server error: " + e.getMessage());
        }

        String jsonResponse = new com.google.gson.Gson().toJson(responseData);
        System.out.println("Sending response: " + jsonResponse);  // 添加日志
        response.getWriter().write(jsonResponse);
    }

    private boolean verifyRecaptcha(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("https://www.google.com/recaptcha/api/siteverify");
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.setEntity(new UrlEncodedFormEntity(Arrays.asList(new NameValuePair[]{new BasicNameValuePair("secret", ConfigManager.getRecaptchaSecretKey()), new BasicNameValuePair("response", token)})));
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
