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
import com.google.gson.Gson;


//@WebServlet("/api/movie-full-search")
public class MovieFullTextSearchServlet extends HttpServlet {
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
        String query = request.getParameter("query");
        if (query == null || query.trim().isEmpty()) {
            response.setContentType("application/json");
            response.getWriter().write("[]");
            return;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getConnection()) {
            // 创建词边界匹配条件
            String[] keywords = query.trim().split("\\s+");
            StringBuilder sqlBuilder = new StringBuilder();

            sqlBuilder.append("SELECT m.id, m.title, m.year, m.director, IFNULL(r.rating, 0) AS rating, m.price ")
                     .append("FROM movies m ")
                     .append("LEFT JOIN ratings r ON m.id = r.movieId ")
                     .append("WHERE ");

            // 构建每个关键词的匹配条件
            for (int i = 0; i < keywords.length; i++) {
                if (i > 0) {
                    sqlBuilder.append(" AND ");
                }
                // 对每个关键词，匹配以下模式：
                // 1. 标题以关键词开头
                // 2. 标题中某个单词以关键词开头（前面有空格）
                sqlBuilder.append("(m.title LIKE ? OR m.title LIKE ?)");
            }

            sqlBuilder.append(" ORDER BY IFNULL(r.rating, 0) DESC, m.title ASC LIMIT 100");

            String sql = sqlBuilder.toString();

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                for (String keyword : keywords) {
                    // 1. 标题以关键词开头
                    stmt.setString(paramIndex++, keyword + "%");
                    // 2. 标题中某个单词以关键词开头（前面有空格）
                    stmt.setString(paramIndex++, "% " + keyword + "%");
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
 