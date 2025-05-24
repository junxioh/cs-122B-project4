package main.java.com.fabflix.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import main.java.com.fabflix.util.DatabaseUtil;

/**
 * shop cart Servlet
 * tackle shop cart's add, update and delete meothods
 */
//@WebServlet("/shopping-cart")
public class ShoppingCartServlet extends HttpServlet {
    private static final long serialVersionUID = 5L;
    private static final String CART_ATTRIBUTE_NAME = "cart";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Map<String, Integer> cart = null;
        List<Map<String, Object>> cartItemsDetails = new ArrayList<>();
        BigDecimal cartTotal = BigDecimal.ZERO;

        if (session != null) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> sessionCart = (Map<String, Integer>) session.getAttribute(CART_ATTRIBUTE_NAME);
            cart = sessionCart;
        }

        if (cart != null && !cart.isEmpty()) {
            try (Connection connection = DatabaseUtil.getConnection()) {
                String placeHolders = String.join(",", Collections.nCopies(cart.size(), "?"));
                String query = "SELECT id, title, price FROM movies WHERE id IN (" + placeHolders + ")";

                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    int index = 1;
                    for (String movieId : cart.keySet()) {
                        statement.setString(index++, movieId);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            String movieId = rs.getString("id");
                            int quantity = cart.get(movieId);
                            BigDecimal price = rs.getBigDecimal("price");
                            BigDecimal itemTotal = price.multiply(BigDecimal.valueOf(quantity));

                            Map<String, Object> item = new HashMap<>();
                            item.put("id", movieId);
                            item.put("title", rs.getString("title"));
                            item.put("price", price);
                            item.put("quantity", quantity);
                            item.put("itemTotal", itemTotal);

                            cartItemsDetails.add(item);
                            cartTotal = cartTotal.add(itemTotal);
                        }
                    }
                }
                cartItemsDetails.sort(Comparator.comparing(item -> (String) item.get("title")));
            } catch (SQLException e) {
                response.getWriter().write("{\"error\":\"Error retrieving cart data.\"}");
                return;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("cartItems", cartItemsDetails);
        result.put("cartTotal", cartTotal);

        if (session != null) {
            String message = (String) session.getAttribute("cartMessage");
            if (message != null) {
                result.put("message", message);
                session.removeAttribute("cartMessage");
            }
            String error = (String) session.getAttribute("cartError");
            if (error != null) {
                result.put("errorMessage", error);
                session.removeAttribute("cartError");
            }
        }

        String json = new com.google.gson.Gson().toJson(result);
        response.getWriter().write(json);
    }








    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("text/html;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        request.setCharacterEncoding("UTF-8");
        
        String action = request.getParameter("action");
        HttpSession session = request.getSession(); // Get session, create if needed

        // Retrieve cart from session, or create a new one if it doesn't exist
        @SuppressWarnings("unchecked")
        Map<String, Integer> cart = (Map<String, Integer>) session.getAttribute(CART_ATTRIBUTE_NAME);
        if (cart == null) {
            cart = new HashMap<>();
            session.setAttribute(CART_ATTRIBUTE_NAME, cart);
        }

        String movieId = request.getParameter("movieId");
        String movieTitle = request.getParameter("movieTitle"); // For messages

        // Default redirect back to movie list (can be overridden)
        String redirectPage = request.getContextPath() + "/movie-list";

        try {
            if ("add".equals(action) && movieId != null) {
                // Add item to cart (increment quantity if already exists)
                cart.put(movieId, cart.getOrDefault(movieId, 0) + 1);
                request.getServletContext().log("Added to cart: Movie ID " + movieId + ", Quantity: " + cart.get(movieId));
                

                String successMessage = "Successfully added '" + (movieTitle != null ? movieTitle : "Movie ID " + movieId) + "' to cart.";
                

                session.setAttribute("cartMessage", successMessage);


                String referer = request.getHeader("Referer");
                if (referer != null && !referer.isEmpty()) {

                    if (referer.contains("single-movie")) {

                        if (referer.contains("?")) {

                            redirectPage = referer + "&cartMessage=" + java.net.URLEncoder.encode(successMessage, "UTF-8");
                        } else {

                            redirectPage = referer + "?cartMessage=" + java.net.URLEncoder.encode(successMessage, "UTF-8");
                        }
                    } else {

                        redirectPage = referer;
                    }
                } else {

                    redirectPage = request.getContextPath() + "/single-movie?id=" + movieId + "&cartMessage=" + 
                                   java.net.URLEncoder.encode(successMessage, "UTF-8");
                }
                

                request.getServletContext().log("Redirecting to: " + redirectPage);

            } else if ("update".equals(action) && movieId != null) {
                 // Handle quantity update
                 try {
                     int quantity = Integer.parseInt(request.getParameter("quantity"));
                     if (quantity > 0) {
                         cart.put(movieId, quantity); // Update quantity
                         session.setAttribute("cartMessage", "Updated quantity for movie ID " + movieId);
                     } else {
                         // Remove item if quantity is 0 or less
                         cart.remove(movieId);
                         session.setAttribute("cartMessage", "Removed movie ID " + movieId + " from cart.");
                     }
                 } catch (NumberFormatException e) {
                     session.setAttribute("cartError", "Invalid quantity provided for movie ID " + movieId);
                 }
                 redirectPage = request.getContextPath() + "/shopping-cart"; // Redirect to cart page after update

            } else if ("remove".equals(action) && movieId != null) {
                 // Handle item removal
                 if (cart.containsKey(movieId)) {
                    cart.remove(movieId);
                    session.setAttribute("cartMessage", "Removed movie ID " + movieId + " from cart.");
                 } else {
                     session.setAttribute("cartError", "Item not found in cart: " + movieId);
                 }
                 redirectPage = request.getContextPath() + "/shopping-cart"; // Redirect to cart page after removal

            } else if ("clear".equals(action)) {
                 // --- Handle clearing the cart ---
                 cart.clear();
                 request.getServletContext().log("Cart cleared");
                 session.setAttribute("cartMessage", "Your cart has been cleared.");
                 redirectPage = request.getContextPath() + "/shopping-cart"; // Redirect to cart page after clear

            } else {
                 request.getServletContext().log("Unknown or invalid cart action: " + action);
                 session.setAttribute("cartError", "Invalid cart operation.");
                 redirectPage = request.getContextPath() + "/shopping-cart"; // Go to cart page on error
            }

            // Store the updated cart back into the session (important!)
            session.setAttribute(CART_ATTRIBUTE_NAME, cart);

        } catch (Exception e) {
             request.getServletContext().log("Error processing cart action: " + action, e);
             session.setAttribute("cartError", "Error processing cart request: " + e.getMessage());
             redirectPage = request.getContextPath() + "/shopping-cart"; // Redirect to cart on error
        }

        // Redirect to the appropriate page
        try {
            request.getServletContext().log("Final redirect to: " + redirectPage);
            response.sendRedirect(redirectPage);
            response.flushBuffer(); //
        } catch (Exception e) {
            request.getServletContext().log("Error during redirect: " + e.getMessage(), e);
        }
    }
}
