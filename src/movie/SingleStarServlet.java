package movie;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import common.DatabaseUtil;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//@WebServlet("/single-star")
public class SingleStarServlet extends HttpServlet {
    private static final long serialVersionUID = 4L;

    // REMOVED Database connection information (now in DatabaseUtil)
    // private static final String DB_USER = "root";
    // private static final String DB_PASSWORD = "root";
    // private static final String DB_URL = "jdbc:mysql://localhost:3306/moviedb";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String starId = request.getParameter("id");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (starId == null || starId.trim().isEmpty()) {
            response.getWriter().write("{\"error\":\"Missing star ID.\"}");
            return;
        }

        Connection connection = null;
        try {
            connection = DatabaseUtil.getConnection();

            Map<String, Object> starInfo = null;
            List<Map<String, String>> moviesStarredIn = new ArrayList<>();

            // obtain Star info
            String starQuery = "SELECT id, name, birthYear FROM stars WHERE id = ?";
            try (PreparedStatement starStatement = connection.prepareStatement(starQuery)) {
                starStatement.setString(1, starId);
                try (ResultSet rs = starStatement.executeQuery()) {
                    if (rs.next()) {
                        starInfo = new HashMap<>();
                        starInfo.put("id", rs.getString("id"));
                        starInfo.put("name", rs.getString("name"));
                        Integer birthYear = rs.getInt("birthYear");
                        if (rs.wasNull()) {
                            starInfo.put("birthYear", "N/A");
                        } else {
                            starInfo.put("birthYear", birthYear);
                        }
                    }
                }
            }

            // Get movies starred by this star
            if (starInfo != null) {
                String moviesQuery = "SELECT m.id, m.title, m.year " +
                        "FROM movies m JOIN stars_in_movies sm ON m.id = sm.movieId " +
                        "WHERE sm.starId = ? ORDER BY m.year DESC, m.title ASC";

                try (PreparedStatement moviesStatement = connection.prepareStatement(moviesQuery)) {
                    moviesStatement.setString(1, starId);
                    try (ResultSet rs = moviesStatement.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> movie = new HashMap<>();
                            movie.put("id", rs.getString("id"));
                            movie.put("title", rs.getString("title"));
                            movie.put("year", rs.getString("year"));
                            moviesStarredIn.add(movie);
                        }
                    }
                }
                starInfo.put("movies", moviesStarredIn);

                String json = new com.google.gson.Gson().toJson(Map.of("star", starInfo));
                response.getWriter().write(json);
            } else {
                response.getWriter().write("{\"error\":\"Star not found.\"}");
            }

        } catch (Exception e) {
            response.getWriter().write("{\"error\":\"Error retrieving star data.\"}");
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }
}


