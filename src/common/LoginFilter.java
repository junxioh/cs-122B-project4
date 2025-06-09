package common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.SignatureException;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebFilter(filterName = "LoginFilter", urlPatterns = "/*")
public class LoginFilter implements Filter {
    private static final Logger logger = Logger.getLogger(LoginFilter.class.getName());
    
    private static final String[] EXCLUDED_PATHS = {
            "/api/login",
            "/login.html",
            "/login.js",
            "/index.html",
            "/",
            "/api/movies/search",
            "/api/movies/browse"
    };

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Initializing LoginFilter");
        // No JWT test during initialization to prevent startup failures
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        logger.info("Processing request for path: " + path);

        // Check if the path is excluded from authentication
        for (String excludedPath : EXCLUDED_PATHS) {
            if (path.equals(excludedPath) || path.endsWith(excludedPath)) {
                logger.info("Path is excluded from authentication: " + path);
                chain.doFilter(request, response);
                return;
            }
        }

        // Allow static resources
        if (path.endsWith(".css") || path.endsWith(".js") || 
            path.endsWith(".png") || path.endsWith(".jpg") || 
            path.endsWith(".gif")) {
            logger.info("Static resource is excluded from authentication: " + path);
            chain.doFilter(request, response);
            return;
        }

        // Get JWT from cookies
        Cookie[] cookies = httpRequest.getCookies();
        String jwt = null;
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("jwt".equals(cookie.getName())) {
                    jwt = cookie.getValue();
                    break;
                }
            }
        }

        if (jwt == null) {
            logger.info("No JWT token found in request, redirecting to login page");
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.html");
            return;
        }

        try {
            Claims claims = JwtUtil.decodeJWT(jwt);
            // Add user info to request attributes for use in servlets
            request.setAttribute("user_id", claims.getId());
            request.setAttribute("email", claims.getSubject());
            request.setAttribute("role", claims.get("role", String.class));
            logger.info("JWT token validated successfully for user: " + claims.getSubject());
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            logger.log(Level.WARNING, "JWT token expired", e);
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.html");
        } catch (SignatureException e) {
            logger.log(Level.WARNING, "Invalid JWT token", e);
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.html");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing JWT token", e);
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/login.html");
        }
    }

    @Override
    public void destroy() {
        logger.info("Destroying LoginFilter");
    }
}
