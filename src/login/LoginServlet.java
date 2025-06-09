package login;

import com.google.gson.JsonObject;
import common.DatabaseUtil;
import common.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;

public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        JsonObject responseJsonObject = new JsonObject();

        try (Connection conn = DatabaseUtil.getConnection()) {
            String query = "SELECT id, email, password FROM customers WHERE email = ?";
            try (PreparedStatement statement = conn.prepareStatement(query)) {
                statement.setString(1, email);

                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("password");
                        String userId = rs.getString("id");
                        
                        // Generate JWT token
                        String token = JwtUtil.generateJWT(userId, email, "customer");

                        responseJsonObject.addProperty("status", "success");
                        responseJsonObject.addProperty("message", "success");
                        responseJsonObject.addProperty("token", token);
                    } else {
                        responseJsonObject.addProperty("status", "fail");
                        responseJsonObject.addProperty("message", "incorrect email or password");
                    }
                }
            }
        } catch (SQLException e) {
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        response.setContentType("application/json");
        response.getWriter().write(responseJsonObject.toString());
    }
}
