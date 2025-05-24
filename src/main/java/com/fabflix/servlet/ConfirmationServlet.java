package main.java.com.fabflix.servlet;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/confirmation")
public class ConfirmationServlet extends HttpServlet {
    private static final long serialVersionUID = 4L;
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (session == null) {
            response.getWriter().write(gson.toJson(Map.of(
                    "status", "fail",
                    "message", "Session expired or invalid."
            )));
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> confirmedItems = (List<Map<String, Object>>) session.getAttribute("confirmedItems");
        BigDecimal finalTotal = (BigDecimal) session.getAttribute("finalTotal");
        LocalDate saleDate = (LocalDate) session.getAttribute("saleDate");

        if (confirmedItems == null || finalTotal == null) {
            response.getWriter().write(gson.toJson(Map.of(
                    "status", "fail",
                    "message", "No order confirmation found."
            )));
        } else {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("confirmedItems", confirmedItems);
            result.put("finalTotal", finalTotal);
            result.put("saleDate", saleDate != null ? saleDate.toString() : null);

            response.getWriter().write(gson.toJson(result));
        }

        // Clean session attributes
        session.removeAttribute("confirmedItems");
        session.removeAttribute("finalTotal");
        session.removeAttribute("saleDate");
    }
}
