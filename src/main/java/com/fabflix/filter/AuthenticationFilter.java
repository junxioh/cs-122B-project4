package main.java.com.fabflix.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class AuthenticationFilter implements Filter {

    private final ArrayList<String> allowedURIs = new ArrayList<>(Arrays.asList(
            "/", "/index.html",

            "/login",
            "/login.html",
            "/login.js",
            "/api/config/recaptcha-key",
            "/verify-recaptcha",
            "/cs122b-project3-recaptcha-example.html",

            // Dashboard paths
            "/_dashboard/login.html",
            "/_dashboard/dashboard.html",
            "/_dashboard/api/login",
            "/_dashboard/api/check-login",
            "/_dashboard/api/metadata",
            "/_dashboard/api/add-star",
            "/_dashboard/api/add-movie",
            "/_dashboard/api/logout",
            "/api/config/recaptcha-key",
            "/movie-list.html",
            "/movie-list.js",

            "/single-movie.html",
            "/single-movie.js",

            "/single-star.html",
            "/single-star.js",

            "/shopping-cart.html",
            "/shopping-cart.js",

            "/payment.html",
            "/payment.js",

            "/confirmation.html",
            "/confirmation.js",

            "/api/confirmation",
            "/api/movie-search",
            "/styles.css"
    ));

    private final ArrayList<String> staticResourcePrefixes = new ArrayList<>(Arrays.asList(
            "/css", "/js", "/images", "/favicon.ico"
    ));

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        filterConfig.getServletContext().log("AuthenticationFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);

        String requestURI = httpRequest.getRequestURI();
        String contextPath = httpRequest.getContextPath();
        String pathWithinApp = requestURI.substring(contextPath.length());

        System.out.println("AuthenticationFilter: Processing request for " + pathWithinApp);

        boolean isLoggedIn = (session != null && session.getAttribute("user") != null);
        boolean isEmployeeLoggedIn = (session != null && session.getAttribute("employee") != null);
        boolean isAllowed = isAllowedURI(pathWithinApp);

        System.out.println("AuthenticationFilter: isLoggedIn=" + isLoggedIn +
                ", isEmployeeLoggedIn=" + isEmployeeLoggedIn +
                ", isAllowed=" + isAllowed);

        if (isLoggedIn || isEmployeeLoggedIn || isAllowed) {
            System.out.println("AuthenticationFilter: Allowing access to " + pathWithinApp);
            chain.doFilter(request, response);
        } else {
            System.out.println("AuthenticationFilter: Unauthorized access attempt to: " + pathWithinApp);
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private boolean isAllowedURI(String pathWithinApp) {
        if (allowedURIs.contains(pathWithinApp)) {
            return true;
        }
        for (String prefix : staticResourcePrefixes) {
            if (pathWithinApp.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        // Nothing to clean up
    }
}
