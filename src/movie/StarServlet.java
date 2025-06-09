package movie;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import common.DatabaseUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

//@WebServlet("/api/stars")
public class StarServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        JsonArray starsArray = new JsonArray();

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM stars LIMIT ?")) {

            ps.setInt(1, 10);  // Set LIMIT parameter
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                JsonObject starJson = new JsonObject();
                    starJson.addProperty("id", rs.getString("id"));
                    starJson.addProperty("name", rs.getString("name"));

                    String birthYear = rs.getString("birthYear");
                    if (rs.wasNull()) {
                    starJson.addProperty("birthYear", "N/A");
                } else {
                    starJson.addProperty("birthYear", birthYear);
                }

                starsArray.add(starJson);
                }
            }

            // return whole array
            out.write(starsArray.toString());

        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);

            JsonObject errorObject = new JsonObject();
            errorObject.addProperty("errorMessage", "Exception: " + e.getMessage());
            out.write(errorObject.toString());
        }

        out.close();
    }
}



