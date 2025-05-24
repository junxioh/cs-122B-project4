package main.java.com.fabflix.servlet;

import main.java.com.fabflix.util.DatabaseUtil;
import com.google.gson.Gson;
import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
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

        /* -------- session & cart -------- */
        HttpSession session = req.getSession(false);
        if(session==null || session.getAttribute("user")==null){
            fail(resp,out,"Not logged in."); return;
        }
        @SuppressWarnings("unchecked")
        Map<String,Integer> cart =
                (Map<String,Integer>) session.getAttribute(CART_ATTR);
        if(cart==null || cart.isEmpty()){
            fail(resp,out,"Your cart is empty."); return;
        }

        /* -------- form fields -------- */
        String first   = trim(req.getParameter("firstName"));
        String last    = trim(req.getParameter("lastName"));
        String cardNum = trim(req.getParameter("cardNumber")).replaceAll("[^\\d]","");
        String expStr  = trim(req.getParameter("expirationDate"));
        if(first==null||last==null||cardNum.isEmpty()||expStr==null){
            fail(resp,out,"All payment fields are required."); return;
        }

        /* 支持 MM/dd/yyyy 与 yyyy-MM-dd */
        LocalDate expDate;
        DateTimeFormatter mmddyyyy = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        try{ expDate = LocalDate.parse(expStr, mmddyyyy); }
        catch(Exception e1){
            try{ expDate = LocalDate.parse(expStr); }
            catch(Exception e2){
                fail(resp,out,"Invalid expiration date format."); return;
            }
        }

        /* -------- validate card -------- */
        if(!validCard(cardNum, first, last, expDate)){
            fail(resp,out,"Invalid credit card information."); return;
        }

        /* -------- write sales + build confirmation -------- */
        int customerId =
                (int)((Map<String,Object>)session.getAttribute("user")).get("id");
        LocalDate saleDate = LocalDate.now();
        List<Map<String,Object>> confirmed = new ArrayList<>();
        BigDecimal finalTotal = BigDecimal.ZERO;

        try(Connection conn = DatabaseUtil.getConnection()){
            conn.setAutoCommit(false);

            /* 1. fetch movie title & price */
            String placeholders = String.join(",", Collections.nCopies(cart.size(),"?" ));
            String movieSql = "SELECT id,title,price FROM movies WHERE id IN ("+placeholders+")";
            Map<String,Map<String,Object>> movieInfo = new HashMap<>();

            try(PreparedStatement ps = conn.prepareStatement(movieSql)){
                int idx=1; for(String id:cart.keySet()) ps.setString(idx++,id);
                try(ResultSet rs = ps.executeQuery()){
                    while(rs.next()){
                        Map<String,Object> m = new HashMap<>();
                        m.put("title", rs.getString("title"));
                        m.put("price", rs.getBigDecimal("price"));
                        movieInfo.put(rs.getString("id"), m);
                    }
                }
            }

            /* 2. insert sales & assemble confirmation */
            String insert = "INSERT INTO sales (customerId,movieId,saleDate,quantity) "
                    + "VALUES (?,?,?,?)";
            try(PreparedStatement ps = conn.prepareStatement(insert)){
                for(Map.Entry<String,Integer> e : cart.entrySet()){
                    String mid = e.getKey();
                    int qty    = e.getValue();
                    BigDecimal price = (BigDecimal)movieInfo.get(mid).get("price");
                    BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(qty));

                    // sales row
                    ps.setInt   (1, customerId);
                    ps.setString(2, mid);
                    ps.setDate  (3, java.sql.Date.valueOf(saleDate)); // ← 明确 java.sql.Date
                    ps.setInt   (4, qty);
                    ps.addBatch();

                    // confirmation row
                    Map<String,Object> row = new HashMap<>();
                    row.put("title", movieInfo.get(mid).get("title"));
                    row.put("price", price);
                    row.put("quantity", qty);
                    row.put("itemTotal", itemTotal);
                    confirmed.add(row);

                    finalTotal = finalTotal.add(itemTotal);
                }
                ps.executeBatch();
            }
            conn.commit();
        }catch(SQLException ex){
            fail(resp,out,"Error processing your order."); return;
        }

        /* 3. stash confirmation info in session */
        session.setAttribute("confirmedItems", confirmed);
        session.setAttribute("finalTotal",    finalTotal);
        session.setAttribute("saleDate",      saleDate);

        /* 4. clear cart */
        cart.clear();
        session.setAttribute(CART_ATTR, cart);

        /* 5. success */
        out.put("success", true);
        resp.getWriter().write(gson.toJson(out));
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
            ps.setDate   (4, java.sql.Date.valueOf(exp));   // ← 明确 java.sql.Date
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
