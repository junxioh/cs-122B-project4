package movie;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;

public class MovieServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        String pathInfo = request.getPathInfo();

        if (pathInfo == null || pathInfo.equals("/")) {
            // List all movies
            listMovies(response);
        } else {
            // Get specific movie
            String movieId = pathInfo.substring(1);
            getMovie(response, movieId);
        }
    }

    private void listMovies(HttpServletResponse response) throws IOException {
        JsonArray jsonArray = new JsonArray();
        
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/moviedb", "root", "root123");
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM movies LIMIT 100")) {

            while (rs.next()) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("id", rs.getString("id"));
                jsonObject.addProperty("title", rs.getString("title"));
                jsonObject.addProperty("year", rs.getInt("year"));
                jsonObject.addProperty("director", rs.getString("director"));
                jsonArray.add(jsonObject);
            }

        } catch (SQLException e) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("status", "error");
            jsonObject.addProperty("message", e.getMessage());
            response.getWriter().write(jsonObject.toString());
            return;
        }

        response.getWriter().write(jsonArray.toString());
    }

    private void getMovie(HttpServletResponse response, String movieId) throws IOException {
        JsonObject jsonObject = new JsonObject();

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/moviedb", "root", "root123");
             PreparedStatement statement = conn.prepareStatement("SELECT * FROM movies WHERE id = ?")) {

            statement.setString(1, movieId);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                jsonObject.addProperty("id", rs.getString("id"));
                jsonObject.addProperty("title", rs.getString("title"));
                jsonObject.addProperty("year", rs.getInt("year"));
                jsonObject.addProperty("director", rs.getString("director"));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                jsonObject.addProperty("status", "error");
                jsonObject.addProperty("message", "Movie not found");
            }

        } catch (SQLException e) {
            jsonObject.addProperty("status", "error");
            jsonObject.addProperty("message", e.getMessage());
        }

        response.getWriter().write(jsonObject.toString());
    }
} 