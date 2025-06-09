package movie;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import common.ConfigManager;
import java.io.IOException;

public class RecaptchaKeyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
    String siteKey = ConfigManager.getRecaptchaSiteKey();
    System.out.println("RecaptchaKeyServlet: Returning site key: " + siteKey);

    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(siteKey);
}
}


