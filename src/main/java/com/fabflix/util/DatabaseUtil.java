package main.java.com.fabflix.util;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据库连接工具类，使用Tomcat JDBC连接池
 */
public class DatabaseUtil {
    private static final String JNDI_NAME = "java:comp/env/jdbc/moviedb";

    // 操作系统检测
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("linux");

    // 连接池数据源实例
    private static DataSource dataSource = null;

    static {
        try {
            // 初始化JNDI上下文
            Context initContext = new InitialContext();
            // 获取连接池数据源
            dataSource = (DataSource) initContext.lookup(JNDI_NAME);

            // 验证连接池配置
            if (dataSource == null) {
                throw new NamingException("DataSource could not be found: " + JNDI_NAME);
            }

            System.out.println("Database connection pool initialized successfully");
            System.out.println("Operating System: " + System.getProperty("os.name"));

            // 测试一次连接以验证连接池配置
            try (Connection testConn = dataSource.getConnection()) {
                if (testConn != null) {
                    System.out.println("Connection pool test successful");
                }
            }
        } catch (NamingException | SQLException e) {
            System.err.println("Error initializing connection pool: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 从连接池获取数据库连接
     * @return 数据库连接
     * @throws SQLException 如果获取连接失败
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        Connection conn = dataSource.getConnection();

        // 记录连接获取信息，标识操作系统
        String osType = IS_LINUX ? "Linux" : (IS_WINDOWS ? "Windows" : "Mac");
        System.out.printf("[%s] Database connection obtained from connection pool\n", osType);

        return conn;
    }

    /**
     * 关闭连接（返回给连接池）
     * @param conn 要关闭的连接
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close(); // 注意：这只是将连接返回给连接池，而不是真正关闭
                System.out.println("Connection returned to pool");
            } catch (SQLException e) {
                System.err.println("Error returning connection to pool: " + e.getMessage());
            }
        }
    }
}
