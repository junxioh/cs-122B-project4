package common;

import com.google.gson.Gson;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebFilter("/_dashboard/*")
public class DashboardAuthFilter implements Filter {
    private final Gson gson = new Gson();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        filterConfig.getServletContext().log("DashboardAuthFilter initialized");
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

        System.out.println("DashboardAuthFilter: Processing request for " + pathWithinApp);

        // Check if it's a login-related request or API request
        boolean isLoginRequest = pathWithinApp.equals("/_dashboard/api/login");
        boolean isLoginPage = pathWithinApp.endsWith("/_dashboard/login.html");
        boolean isApiRequest = pathWithinApp.startsWith("/_dashboard/api/");
        boolean isStaticResource = pathWithinApp.endsWith(".html") ||
                pathWithinApp.endsWith(".css") ||
                pathWithinApp.endsWith(".js") ||
                pathWithinApp.endsWith(".png") ||
                pathWithinApp.endsWith(".jpg") ||
                pathWithinApp.endsWith(".jpeg") ||
                pathWithinApp.endsWith(".gif");

        System.out.println("DashboardAuthFilter: isLoginRequest=" + isLoginRequest +
                ", isLoginPage=" + isLoginPage +
                ", isApiRequest=" + isApiRequest +
                ", isStaticResource=" + isStaticResource);

        // Allow login page, login request, or static resources
        if (isLoginRequest || isLoginPage || isStaticResource) {
            System.out.println("DashboardAuthFilter: Allowing access to " + pathWithinApp);
            chain.doFilter(request, response);
            return;
        }

        // For API requests, check if logged in
        if (isApiRequest) {
            if (session != null && session.getAttribute("employee") != null) {
                System.out.println("DashboardAuthFilter: User is logged in, allowing API access");
                chain.doFilter(request, response);
            } else {
                System.out.println("DashboardAuthFilter: User is not logged in, returning 401 for API request");
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("status", "error");
                responseData.put("message", "Unauthorized");
                httpResponse.getWriter().write(gson.toJson(responseData));
            }
            return;
        }

        // For other requests, if logged in, allow access
        if (session != null && session.getAttribute("employee") != null) {
            System.out.println("DashboardAuthFilter: User is logged in, allowing access");
            chain.doFilter(request, response);
        } else {
            // Not logged in, redirect to login page
            System.out.println("DashboardAuthFilter: User is not logged in, redirecting to login page");
            httpResponse.sendRedirect(contextPath + "/_dashboard/login.html");
        }
    }

    @Override
    public void destroy() {
        // Nothing to clean up
    }
}
