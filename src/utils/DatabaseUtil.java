package utils;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database connection utility class, supporting JNDI connection pool and direct connection
 */
public class DatabaseUtil {
    private static final String JNDI_NAME = "java:comp/env/jdbc/moviedb";

    // Operating system detection
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_WINDOWS = OS.contains("win");
    private static final boolean IS_LINUX = OS.contains("linux");

    // Connection pool data source instance
    private static DataSource dataSource = null;
    // Whether to use direct connection
    private static boolean useDirectConnection = false;
    // Direct connection parameters
    private static String dbUrl = null;
    private static String dbUser = null;
    private static String dbPassword = null;

    static {
        try {
            // Check environment variables to determine whether to use direct connection
            String k8sEnv = System.getenv("KUBERNETES_ENV");
            if (k8sEnv != null && k8sEnv.equals("true")) {
                useDirectConnection = true;
                dbUrl = System.getenv("DB_URL");
                dbUser = System.getenv("DB_USER");
                dbPassword = System.getenv("DB_PASSWORD");

                if (dbUrl == null || dbUser == null || dbPassword == null) {
                    System.err.println("Error: DB_URL, DB_USER, or DB_PASSWORD environment variable not set");
                } else {
                    System.out.println("Using direct database connection in Kubernetes environment");
                    // Load database driver
                    try {
                        Class.forName("com.mysql.cj.jdbc.Driver");
                        System.out.println("MySQL JDBC Driver loaded successfully");
                    } catch (ClassNotFoundException e) {
                        System.err.println("Error loading MySQL JDBC driver: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                // Use JNDI connection pool
                try {
                    // Initialize JNDI context
                    Context initContext = new InitialContext();
                    // Get connection pool data source
                    dataSource = (DataSource) initContext.lookup(JNDI_NAME);

                    // Validate connection pool configuration
                    if (dataSource == null) {
                        throw new NamingException("DataSource could not be found: " + JNDI_NAME);
                    }

                    System.out.println("Database connection pool initialized successfully");
                    System.out.println("Operating System: " + System.getProperty("os.name"));

                    // Test connection to verify connection pool configuration
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
        } catch (Exception e) {
            System.err.println("Error during DatabaseUtil initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get database connection
     * @return database connection
     * @throws SQLException if connection acquisition fails
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = null;

        if (useDirectConnection) {
            // Direct database connection
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            System.out.println("Direct database connection established");
        } else {
            // Use connection pool
            if (dataSource == null) {
                throw new SQLException("DataSource is not initialized");
            }
            conn = dataSource.getConnection();

            // Log connection acquisition information, identify operating system
            String osType = IS_LINUX ? "Linux" : (IS_WINDOWS ? "Windows" : "Mac");
            System.out.printf("[%s] Database connection obtained from connection pool\n", osType);
        }

        return conn;
    }

    /**
     * Close connection
     * @param conn connection to close
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
                if (useDirectConnection) {
                    System.out.println("Direct connection closed");
                } else {
                    System.out.println("Connection returned to pool");
                }
            } catch (SQLException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
    }
}
