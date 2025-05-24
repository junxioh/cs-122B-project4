package main.java.com.fabflix.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

import java.io.IOException;

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

        // 检查是否是登录相关的请求或API请求
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

        // 如果是登录页面、登录请求或静态资源，直接放行
        if (isLoginRequest || isLoginPage || isStaticResource) {
            System.out.println("DashboardAuthFilter: Allowing access to " + pathWithinApp);
            chain.doFilter(request, response);
            return;
        }

        // 如果是API请求，检查是否已登录
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

        // 对于其他请求，如果已登录，允许访问
        if (session != null && session.getAttribute("employee") != null) {
            System.out.println("DashboardAuthFilter: User is logged in, allowing access");
            chain.doFilter(request, response);
            return;
        }

        // 未登录，重定向到登录页面
        System.out.println("DashboardAuthFilter: User is not logged in, redirecting to login page");
        httpResponse.sendRedirect(contextPath + "/_dashboard/login.html");
    }

    @Override
    public void destroy() {
    }
}
