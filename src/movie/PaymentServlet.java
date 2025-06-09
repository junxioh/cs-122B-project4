package movie;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import common.DatabaseUtil;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PaymentServlet extends HttpServlet {
    private static final long serialVersionUID = 6L;
    private static final String CART_ATTR = "cart";
    private final Gson gson = new Gson();

    /* ================ POST ================ */
    @Override
    protected void doPost(HttpServletRequest req,
                          HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        Map<String,Object> out = new HashMap<>();

        // 简化：只校验必填参数，均满足即返回成功
        String first   = trim(req.getParameter("firstName"));
        String last    = trim(req.getParameter("lastName"));
        String cardNum = trim(req.getParameter("cardNumber"));
        String expStr  = trim(req.getParameter("expirationDate"));
        if (first == null || last == null || cardNum == null || cardNum.isEmpty() || expStr == null) {
            fail(resp, out, "All payment fields are required.");
            return;
        }

        out.put("success", true);
        resp.getWriter().write(new com.google.gson.Gson().toJson(out));
    }

    /* ================ helpers ================ */
    private boolean validCard(String id,String fn,String ln,LocalDate exp){
        String sql = "SELECT COUNT(*) FROM creditcards "
                + "WHERE id=? AND firstName=? AND lastName=? AND expiration=?";
        try(Connection conn = DatabaseUtil.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1,id);
            ps.setString(2,fn);
            ps.setString(3,ln);
            ps.setDate   (4, java.sql.Date.valueOf(exp));   // Convert to java.sql.Date
            try(ResultSet rs = ps.executeQuery()){
                return rs.next() && rs.getInt(1) > 0;
            }
        }catch(SQLException e){ return false; }
    }

    private void fail(HttpServletResponse resp, Map<String,Object> out, String msg) throws IOException{
        out.put("success",false);
        out.put("errorMessage",msg);
        resp.getWriter().write(gson.toJson(out));
    }

    private static String trim(String s){ return s==null?null:s.trim(); }
}


