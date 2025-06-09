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


public class SingleMovieServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {

        String movieId = request.getParameter("id");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (movieId == null || movieId.trim().isEmpty()) {
            response.getWriter().write("{\"error\":\"Missing movie ID.\"}");
            return;
        }

        try (Connection conn = DatabaseUtil.getConnection()) {

            Map<String, Object> movie = getMovieDetails(conn, movieId);

            if (movie == null) {
                response.getWriter().write("{\"error\":\"Movie not found.\"}");
                return;
            }

            String json = new com.google.gson.Gson()
                    .toJson(Map.of("movie", movie));
            response.getWriter().write(json);

        } catch (Exception e) {
            request.getServletContext().log("SingleMovieServlet error", e);
            response.getWriter().write("{\"error\":\"Error retrieving movie.\"}");
        }
    }

    private Map<String, Object> getMovieDetails(Connection conn, String id) throws SQLException {
        String sql = "SELECT m.id, m.title, m.year, m.director, "
                + "IFNULL(r.rating,0) AS rating, m.price "
                + "FROM movies m LEFT JOIN ratings r ON m.id = r.movieId "
                + "WHERE m.id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                Map<String, Object> m = new HashMap<>();
                m.put("id",        rs.getString("id"));
                m.put("title",     rs.getString("title"));
                m.put("year",      rs.getInt("year"));
                m.put("director",  rs.getString("director"));
                m.put("rating",    rs.getDouble("rating"));
                m.put("price",     rs.getBigDecimal("price"));

                m.put("genres", getMovieGenres(conn, id));
                m.put("stars",  getMovieStars (conn, id));
                return m;
            }
        }
    }

    private List<Map<String, String>> getMovieGenres(Connection conn, String id) throws SQLException {
        String q = "SELECT g.id, g.name "
                + "FROM genres g JOIN genres_in_movies gim ON g.id = gim.genreId "
                + "WHERE gim.movieId = ? ORDER BY g.name ASC";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, String>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> g = new HashMap<>();
                    g.put("id", rs.getString("id"));
                    g.put("name", rs.getString("name"));
                    list.add(g);
                }
                return list;
            }
        }
    }

    private List<Map<String, String>> getMovieStars(Connection conn, String id) throws SQLException {
        String q = "SELECT s.id, s.name, COUNT(sim2.movieId) AS cnt "
                + "FROM stars s "
                + "JOIN stars_in_movies sim1 ON s.id = sim1.starId AND sim1.movieId = ? "
                + "JOIN stars_in_movies sim2 ON s.id = sim2.starId "
                + "GROUP BY s.id, s.name "
                + "ORDER BY cnt DESC, s.name ASC";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, String>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> star = new HashMap<>();
                    star.put("id",   rs.getString("id"));
                    star.put("name", rs.getString("name"));
                    list.add(star);
                }
                return list;
            }
        }
    }
}


