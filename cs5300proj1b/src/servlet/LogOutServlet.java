package servlet;

import java.io.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;


@WebServlet("/LogOutServlet")
public class LogOutServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException, IOException {
        // doGet and doPost can do the same thing for this servlet
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        Cookie sessionCookie = null;
        SessionState state = null;
        
        // Title for this servlet
        String title = "LogOutServlet";
        
        // start response initialization
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        
        // retrieve cookies
        Cookie[] cookies = request.getCookies();

        // Make sure we have a cookie
        if (null != cookies && 0 < cookies.length) {
            // search through provided cookie for one with correct name
            int i = 0;
            while(i < cookies.length && !cookies[i].getName().equals(Utils.cookieName)) {
                i++;
            }

            // Can't find a correct cookie?
            if(i == cookies.length) {
                out.println(Utils.genHTML(title, "No Session to Log Out Of", ""));
                return;
            }

            sessionCookie = cookies[i];

            // retrieve session state
            //state = SessionState.removeSession(Utils.getCookieSessionId(sessionCookie));
            state = SessionState.readSession(Utils.getCookieSessionId(sessionCookie),
                                            Utils.getCookieVersion(sessionCookie),
                                            Utils.getCookieIps(sessionCookie));

            //check that session retrieved is actually in state table
            if(null == state) {
                out.println(Utils.genHTML(title, "Session Has Expired", ""));
                sessionCookie.setMaxAge(0);
                response.addCookie(sessionCookie);
                return;
            }

            state.incrementVersion();

            out.println(Utils.genHTML(title, "Bye!", ""));

            // Don't bother setting anything extra since we are removing the cookie
            sessionCookie.setMaxAge(0);
            response.addCookie(sessionCookie);
        } else {
            out.println(Utils.genHTML(title, "No Session to Log Out Of", ""));
        }
    }
}
