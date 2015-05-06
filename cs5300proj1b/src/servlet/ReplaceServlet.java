package servlet;

import java.io.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;


@WebServlet("/ReplaceServlet")
public class ReplaceServlet extends HttpServlet {

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        Cookie sessionCookie = null;
        SessionState state = null;
        
        // Title for this servlet
        String title = "ReplaceServlet";

        // Get new message to display
        String msg = request.getParameter("replaceName");
        
        // retrieve cookies
        Cookie[] cookies = request.getCookies();

        // start response initialization
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        // Make sure we have a cookie
        if (null != cookies && 0 < cookies.length) {
            // Search for correct cookie
            int i = 0;
            while(i < cookies.length && !cookies[i].getName().equals(Utils.cookieName)) {
                i++;
            }

            // correct cookie not found?
            if(i == cookies.length) {
                out.println(Utils.genHTML(title, "Session Has Expired", ""));
                return;
            }

            sessionCookie = cookies[i];

            // retrieve session state
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

            // Everything's okay, so update session version and message
            state.incrementVersion();
            state.setMessage(msg);

            // Add session to session table
            String[] hostips = SessionState.writeSession(state, Utils.getCookieIps(sessionCookie));

            // update cookie
            String newCookieVal = 
                Utils.formCookieValue(state.getSessionId(), state.getVersion(), hostips);
            sessionCookie.setValue(newCookieVal);
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
