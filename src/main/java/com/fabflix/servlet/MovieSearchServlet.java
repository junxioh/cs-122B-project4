package main.java.com.fabflix.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import main.java.com.fabflix.util.DatabaseUtil;
import main.java.com.fabflix.util.LevenshteinUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

//@WebServlet("/api/movie-search")
public class MovieSearchServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

//    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        String query = request.getParameter("query");
//        if (query == null || query.trim().isEmpty()) {
//            response.setContentType("application/json");
//            response.getWriter().write("[]");
//            return;
//        }
//
//        List<Map<String, String>> results = new ArrayList<>();
//        try (Connection conn = getConnection()) {
//            String[] keywords = query.trim().split("\\s+");
//
//            // 构建SQL查询，使用LIKE操作符和空格匹配
//            StringBuilder sqlBuilder = new StringBuilder("SELECT id, title FROM movies WHERE ");
//
//            for (int i = 0; i < keywords.length; i++) {
//                if (i > 0) {
//                    sqlBuilder.append(" AND ");
//                }
//                // 对每个关键词，匹配以下模式：
//                // 1. 标题以关键词开头
//                // 2. 标题中某个单词以关键词开头（前面有空格）
//                sqlBuilder.append("(title LIKE ? OR title LIKE ?)");
//            }
//
//            sqlBuilder.append(" ORDER BY title LIMIT 10");
//            String sql = sqlBuilder.toString();
//
//            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
//                int paramIndex = 1;
//                for (String keyword : keywords) {
//                    // 1. 标题以关键词开头
//                    stmt.setString(paramIndex++, keyword + "%");
//                    // 2. 标题中某个单词以关键词开头（前面有空格）
//                    stmt.setString(paramIndex++, "% " + keyword + "%");
//                }
//
//                try (ResultSet rs = stmt.executeQuery()) {
//                    while (rs.next()) {
//                        Map<String, String> movie = new HashMap<>();
//                        movie.put("id", rs.getString("id"));
//                        movie.put("title", rs.getString("title"));
//                        results.add(movie);
//                    }
//                }
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//            return;
//        }
//
//        response.setContentType("application/json");
//        PrintWriter out = response.getWriter();
//        out.print("[");
//        for (int i = 0; i < results.size(); i++) {
//            out.print("{\"id\":\"" + results.get(i).get("id") + "\",\"title\":\"" + results.get(i).get("title") + "\"}");
//            if (i < results.size() - 1) {
//                out.print(",");
//            }
//        }
//        out.print("]");
//    }


    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String query = request.getParameter("query");
        if (query == null || query.trim().isEmpty()) {
            response.setContentType("application/json");
            response.getWriter().write("[]");
            return;
        }

        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = getConnection()) {
            String[] keywords = query.trim().split("\\s+");

            // 构建SQL查询，使用LIKE操作符
            StringBuilder sqlBuilder = new StringBuilder("SELECT id, title FROM movies WHERE ");
            for (int i = 0; i < keywords.length; i++) {
                if (i > 0) {
                    sqlBuilder.append(" AND ");
                }
                sqlBuilder.append("(title LIKE ?)");
            }

            sqlBuilder.append(" ORDER BY title LIMIT 10");
            String sql = sqlBuilder.toString();

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                for (String keyword : keywords) {
                    stmt.setString(paramIndex++, "%" + keyword + "%");
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> movie = new HashMap<>();
                        movie.put("id", rs.getString("id"));
                        movie.put("title", rs.getString("title"));
                        results.add(movie);
                    }
                }
            }

            // 模糊搜索逻辑
            List<Map<String, String>> fuzzyResults = new ArrayList<>();
            for (Map<String, String> movie : results) {
                if (LevenshteinUtil.distance(movie.get("title"), query) <= 2) { // 设定最大编辑距离为2
                    fuzzyResults.add(movie);
                }
            }
            results.addAll(fuzzyResults);
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.print("[");
        for (int i = 0; i < results.size(); i++) {
            out.print("{\"id\":\"" + results.get(i).get("id") + "\",\"title\":\"" + results.get(i).get("title") + "\"}");
            if (i < results.size() - 1) {
                out.print(",");
            }
        }
        out.print("]");
    }

    private Connection getConnection() throws SQLException {
        return DatabaseUtil.getConnection();
    }
}
