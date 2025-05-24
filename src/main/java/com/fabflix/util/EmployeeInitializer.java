package main.java.com.fabflix.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EmployeeInitializer {
    public static void initializeEmployee() {
        String email = "classta@email.edu";
        String password = "classta";
        String fullname = "TA CS122B";
        
        // 加密密码
        String encryptedPassword = PasswordUtil.encryptPassword(password);
        
        try (Connection conn = DatabaseUtil.getConnection()) {
            // 检查员工是否已存在
            String checkSql = "SELECT COUNT(*) FROM employees WHERE email = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, email);
                if (checkStmt.executeQuery().next() && checkStmt.executeQuery().getInt(1) > 0) {
                    System.out.println("Employee already exists");
                    return;
                }
            }
            
            // 插入新员工
            String insertSql = "INSERT INTO employees (email, password, fullname) VALUES (?, ?, ?)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, email);
                insertStmt.setString(2, encryptedPassword);
                insertStmt.setString(3, fullname);
                insertStmt.executeUpdate();
                System.out.println("Employee initialized successfully");
            }
        } catch (SQLException e) {
            System.err.println("Error initializing employee: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 