package main.java.com.fabflix.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import main.java.com.fabflix.util.DatabaseUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

//@WebServlet("/api/movies-by-ids")
public class MoviesByIdsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String idsJson = request.getParameter("ids");
        if (idsJson == null || idsJson.trim().isEmpty()) {
            response.setContentType("application/json");
            response.getWriter().write("[]");
            return;
        }

        // 解析JSON数组
        List<String> movieIds;
        try {
            JsonArray idArray = JsonParser.parseString(idsJson).getAsJsonArray();
            movieIds = new ArrayList<>();
            for (int i = 0; i < idArray.size(); i++) {
                movieIds.add(idArray.get(i).getAsString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid ID format\"}");
            return;
        }

        if (movieIds.isEmpty()) {
            response.setContentType("application/json");
            response.getWriter().write("[]");
            return;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            // 构建IN查询
            String placeholders = movieIds.stream()
                    .map(id -> "?")
                    .collect(Collectors.joining(","));

            String sql = "SELECT m.id, m.title, m.year, m.director, IFNULL(r.rating, 0) AS rating, m.price " +
                         "FROM movies m " +
                         "LEFT JOIN ratings r ON m.id = r.movieId " +
                         "WHERE m.id IN (" + placeholders + ") " +
                         "ORDER BY IFNULL(r.rating, 0) DESC, m.title ASC";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                // 设置ID参数
                for (int i = 0; i < movieIds.size(); i++) {
                    stmt.setString(i + 1, movieIds.get(i));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String movieId = rs.getString("id");
                        Map<String, Object> movie = new HashMap<>();

                        movie.put("id", movieId);
                        movie.put("title", rs.getString("title"));
                        movie.put("year", rs.getInt("year"));
                        movie.put("director", rs.getString("director"));
                        movie.put("rating", rs.getDouble("rating"));
                        movie.put("price", rs.getBigDecimal("price"));
                        movie.put("genres", fetchGenres(conn, movieId, 3));
                        movie.put("stars", fetchStars(conn, movieId, 3));

                        results.add(movie);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error\"}");
            return;
        }

        response.setContentType("application/json");
        Gson gson = new Gson();
        response.getWriter().write(gson.toJson(results));
    }

    private List<Map<String, String>> fetchGenres(Connection c, String mid, int lim) throws SQLException {
        String q = "SELECT g.id, g.name FROM genres g " +
                "JOIN genres_in_movies gim ON g.id = gim.genreId " +
                "WHERE gim.movieId = ? ORDER BY g.name LIMIT ?";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, mid);
            ps.setInt(2, lim);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, String>> l = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> genre = new HashMap<>();
                    genre.put("id", rs.getString(1));
                    genre.put("name", rs.getString(2));
                    l.add(genre);
                }
                return l;
            }
        }
    }

    private List<Map<String, String>> fetchStars(Connection c, String mid, int lim) throws SQLException {
        String q = "SELECT s.id, s.name FROM stars s " +
                "JOIN stars_in_movies sim ON s.id = sim.starId " +
                "WHERE sim.movieId = ? " +
                "ORDER BY (SELECT COUNT(*) FROM stars_in_movies WHERE starId = s.id) DESC, s.name ASC " +
                "LIMIT ?";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, mid);
            ps.setInt(2, lim);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, String>> l = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> star = new HashMap<>();
                    star.put("id", rs.getString(1));
                    star.put("name", rs.getString(2));
                    l.add(star);
                }
                return l;
            }
        }
    }
}
