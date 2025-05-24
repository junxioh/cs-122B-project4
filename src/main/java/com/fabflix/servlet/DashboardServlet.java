package main.java.com.fabflix.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import main.java.com.fabflix.util.DatabaseUtil;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.equals("/")) {
            response.sendRedirect(request.getContextPath() + "/_dashboard/dashboard.html");
            return;
        }

        // 移除开头的/api
        String apiPath = pathInfo.startsWith("/api") ? pathInfo.substring(4) : pathInfo;

        switch (apiPath) {
            case "/check-login":
                checkLogin(request, response);
                break;
            case "/logout":
                logout(request, response);
                break;
            case "/metadata":
                getMetadata(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        
        // 移除开头的/api
        String apiPath = pathInfo.startsWith("/api") ? pathInfo.substring(4) : pathInfo;

        switch (apiPath) {
            case "/add-star":
                addStar(request, response);
                break;
            case "/add-movie":
                addMovie(request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void checkLogin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Map<String, Object> result = new HashMap<>();

        if (session != null && session.getAttribute("employee") != null) {
            result.put("loggedIn", true);
            result.put("employeeName", session.getAttribute("employeeName"));
        } else {
            result.put("loggedIn", false);
        }

        response.setContentType("application/json");
        response.getWriter().write(new com.google.gson.Gson().toJson(result));
    }

    private void logout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getMetadata(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try (Connection conn = DatabaseUtil.getConnection();
             CallableStatement stmt = conn.prepareCall("CALL get_database_metadata()")) {

            List<Map<String, String>> metadata = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new HashMap<>();
                    row.put("TABLE_NAME", rs.getString("TABLE_NAME"));
                    row.put("COLUMN_NAME", rs.getString("COLUMN_NAME"));
                    row.put("COLUMN_TYPE", rs.getString("COLUMN_TYPE"));
                    row.put("IS_NULLABLE", rs.getString("IS_NULLABLE"));
                    row.put("COLUMN_KEY", rs.getString("COLUMN_KEY"));
                    row.put("COLUMN_DEFAULT", rs.getString("COLUMN_DEFAULT"));
                    row.put("EXTRA", rs.getString("EXTRA"));
                    metadata.add(row);
                }
            }

            response.setContentType("application/json");
            response.getWriter().write(new com.google.gson.Gson().toJson(metadata));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void addStar(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            // 解析请求数据
            Map<String, Object> data = new com.google.gson.Gson().fromJson(
                request.getReader(), Map.class);

            String name = (String) data.get("name");
            Integer birthYear = data.get("birthYear") != null ?
                Integer.parseInt(data.get("birthYear").toString()) : null;

            try (Connection conn = DatabaseUtil.getConnection()) {
                // 检查明星是否已存在
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM stars WHERE name = ? AND (? IS NULL OR birthYear = ?)")) {
                    checkPs.setString(1, name);
                    checkPs.setObject(2, birthYear);
                    checkPs.setObject(3, birthYear);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next()) {
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":\"error\",\"message\":\"A star with this name and birth year already exists.\"}");
                            return;
                        }
                    }
                }

                // 生成新的明星ID
                String newId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT CONCAT('nm', LPAD(COALESCE(MAX(SUBSTRING(id, 3)) + 1, 1), 7, '0')) " +
                        "FROM stars WHERE id LIKE 'nm%'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            newId = rs.getString(1);
                        } else {
                            newId = "nm0000001";
                        }
                    }
                }

                // 插入新明星
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stars (id, name, birthYear) VALUES (?, ?, ?)")) {
                    ps.setString(1, newId);
                    ps.setString(2, name);
                    ps.setObject(3, birthYear);
                    ps.executeUpdate();
                }

                response.setContentType("application/json");
                response.getWriter().write("{\"status\":\"success\",\"message\":\"Star added successfully\"}");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private void addMovie(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            // 解析请求数据
            Map<String, Object> data = new com.google.gson.Gson().fromJson(
                request.getReader(), Map.class);

            String title = (String) data.get("title");
            int year = Integer.parseInt(data.get("year").toString());
            String director = (String) data.get("director");

            try (Connection conn = DatabaseUtil.getConnection()) {
                // 检查电影是否已存在
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM movies WHERE title = ? AND year = ? AND director = ?")) {
                    checkPs.setString(1, title);
                    checkPs.setInt(2, year);
                    checkPs.setString(3, director);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next()) {
                            response.setContentType("application/json");
                            response.getWriter().write("{\"status\":\"error\",\"message\":\"A movie with this title, year, and director already exists.\"}");
                            return;
                        }
                    }
                }

                // 检查明星是否存在
                String starName = (String) data.get("starName");
                Integer starBirthYear = data.get("starBirthYear") != null ?
                    Integer.parseInt(data.get("starBirthYear").toString()) : null;

                String starId = null;
                try (PreparedStatement checkStarPs = conn.prepareStatement(
                        "SELECT id FROM stars WHERE name = ? AND (? IS NULL OR birthYear = ?)")) {
                    checkStarPs.setString(1, starName);
                    checkStarPs.setObject(2, starBirthYear);
                    checkStarPs.setObject(3, starBirthYear);
                    try (ResultSet rs = checkStarPs.executeQuery()) {
                        if (rs.next()) {
                            starId = rs.getString("id");
                        }
                    }
                }

                // 如果明星不存在，先创建明星
                if (starId == null) {
                    // 生成新的明星ID
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT CONCAT('nm', LPAD(COALESCE(MAX(SUBSTRING(id, 3)) + 1, 1), 7, '0')) " +
                            "FROM stars WHERE id LIKE 'nm%'")) {
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                starId = rs.getString(1);
                            } else {
                                starId = "nm0000001";
                            }
                        }
                    }

                    // 插入新明星
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO stars (id, name, birthYear) VALUES (?, ?, ?)")) {
                        ps.setString(1, starId);
                        ps.setString(2, starName);
                        ps.setObject(3, starBirthYear);
                        ps.executeUpdate();
                    }
                }

                // 检查类型是否存在
                String genreName = (String) data.get("genreName");
                String genreId = null;
                try (PreparedStatement checkGenrePs = conn.prepareStatement(
                        "SELECT id FROM genres WHERE name = ?")) {
                    checkGenrePs.setString(1, genreName);
                    try (ResultSet rs = checkGenrePs.executeQuery()) {
                        if (rs.next()) {
                            genreId = rs.getString("id");
                        }
                    }
                }

                // 如果类型不存在，创建新类型
                if (genreId == null) {
                    // 插入新类型，让数据库自动生成ID
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO genres (name) VALUES (?)",
                            PreparedStatement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, genreName);
                        ps.executeUpdate();
                        
                        // 获取自动生成的ID
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                genreId = rs.getString(1);
                            }
                        }
                    }
                }

                // 生成新的电影ID
                String movieId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT CONCAT('tt', LPAD(COALESCE(MAX(SUBSTRING(id, 3)) + 1, 1), 7, '0')) " +
                        "FROM movies WHERE id LIKE 'tt%'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            movieId = rs.getString(1);
                        } else {
                            movieId = "tt0000001";
                        }
                    }
                }

                // 开始事务
                conn.setAutoCommit(false);
                try {
                    // 插入电影
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO movies (id, title, year, director, price) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, movieId);
                        ps.setString(2, title);
                        ps.setInt(3, year);
                        ps.setString(4, director);
                        ps.setBigDecimal(5, new java.math.BigDecimal(data.get("price").toString()));
                        ps.executeUpdate();
                    }

                    // 添加明星关联
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO stars_in_movies (starId, movieId) VALUES (?, ?)")) {
                        ps.setString(1, starId);
                        ps.setString(2, movieId);
                        ps.executeUpdate();
                    }

                    // 添加类型关联
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO genres_in_movies (genreId, movieId) VALUES (?, ?)")) {
                        ps.setString(1, genreId);
                        ps.setString(2, movieId);
                        ps.executeUpdate();
                    }

                    // 提交事务
                    conn.commit();
                    response.setContentType("application/json");
                    response.getWriter().write("{\"status\":\"success\",\"message\":\"Movie added successfully\"}");
                } catch (Exception e) {
                    // 回滚事务
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
