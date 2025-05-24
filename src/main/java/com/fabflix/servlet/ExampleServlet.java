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

/**
 * 示例Servlet，演示如何正确使用连接池和PreparedStatement
 */
@WebServlet("/example")
public class ExampleServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // 从请求中获取参数
        String title = request.getParameter("title");
        
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            // 从连接池获取连接
            conn = DatabaseUtil.getConnection();
            
            // 使用预编译语句，避免SQL注入
            String sql = "SELECT id, title, year, director FROM movies WHERE title LIKE ? LIMIT 10";
            stmt = conn.prepareStatement(sql);
            
            // 设置参数
            stmt.setString(1, "%" + (title != null ? title : "") + "%");
            
            // 执行查询
            rs = stmt.executeQuery();
            
            // 构建响应
            StringBuilder sb = new StringBuilder();
            sb.append("<html><head><title>Movie Search Results</title></head><body>");
            sb.append("<h1>Movies matching '").append(title).append("'</h1>");
            sb.append("<table border='1'><tr><th>ID</th><th>Title</th><th>Year</th><th>Director</th></tr>");
            
            int count = 0;
            while (rs.next()) {
                sb.append("<tr>");
                sb.append("<td>").append(rs.getString("id")).append("</td>");
                sb.append("<td>").append(rs.getString("title")).append("</td>");
                sb.append("<td>").append(rs.getInt("year")).append("</td>");
                sb.append("<td>").append(rs.getString("director")).append("</td>");
                sb.append("</tr>");
                count++;
            }
            
            sb.append("</table>");
            sb.append("<p>Found ").append(count).append(" results</p>");
            sb.append("</body></html>");
            
            response.getWriter().write(sb.toString());
            
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } finally {
            // 关闭资源（按照打开的相反顺序）
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) DatabaseUtil.closeConnection(conn); // 将连接返回到连接池
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
} 