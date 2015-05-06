package servlet;

import java.io.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;


@WebServlet("/RefreshServlet")
public class RefreshServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) 
        throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        Cookie sessionCookie = null;
        SessionState state = null;
        
        // Title for this servlet
        String title = "RefreshServlet";
       
        // start response initialization
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        
        // retrieve cookies
        Cookie[] cookies = request.getCookies();

        // Make sure we have a cookie
        if (null != cookies && 0 < cookies.length) {
            // search for correct cookie
            int i = 0;
            while(i < cookies.length && !cookies[i].getName().equals(Utils.cookieName)) {
                i++;
            }

            // Correct cookie not found?
            if(i == cookies.length) {
                out.println(Utils.genHTML(title, "Session Has Expired", ""));
                return;
            }

            // retrieve session state
            sessionCookie = cookies[i];
            //state = SessionState.removeSession(Utils.getCookieSessionId(sessionCookie));
            state = SessionState.readSession(Utils.getCookieSessionId(sessionCookie),
                                            Utils.getCookieVersion(sessionCookie),
                                            Utils.getCookieIps(sessionCookie));

            // check that session retrieved is actually in state table
            if(null == state) {
                out.println(Utils.genHTML(title, "Session Has Expired", ""));
                sessionCookie.setMaxAge(0);
                response.addCookie(sessionCookie);
                return;
            }

            state.incrementVersion();

            // Add session to session table
            String[] hostips = SessionState.writeSession(state, Utils.getCookieIps(sessionCookie));

            // Send user a new cookie
            sessionCookie = new Cookie( Utils.cookieName, 
                    Utils.formCookieValue(state.getSessionId(), state.getVersion(), hostips));
            sessionCookie.setMaxAge(Utils.cookieMaxAge);
            response.addCookie(sessionCookie);

            String extra = Utils.entryForm + "\n" 
                + "<p>Session Cookie: " + Utils.cookieString(sessionCookie) + "</p>";
            out.println(Utils.genHTML(title, state.getMessage(), extra));
 
        } else {
            out.println(Utils.genHTML(title, "Session Has Expired", ""));
        }
    }
}
