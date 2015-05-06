package servlet;

import java.io.*;
import javax.servlet.*;
import javax.servlet.annotation.*;
import javax.servlet.http.*;


@WebServlet("/EnterServlet")
public class EnterServlet extends HttpServlet {

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        // doGet and doPost can do the same thing for this servlet
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        //Initialize Threads that have to run on this instance
        NetUtils.initThreads();
        
        Cookie sessionCookie = null;
        SessionState state = null;
        String[] cookieips = new String[]{};

        // Title for this servlet
        String title = "EnterServlet";

        // start response initialization
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        
        // retrieve cookies
        Cookie[] cookies = request.getCookies();

        if (null == cookies || 0 == cookies.length) {
            // if no cookies, define a new session
            state = SessionState.newSession(Utils.defaultMsg);
        } else {
            // search through provided cookie for one with correct name
            int i = 0;
            while(i < cookies.length && !cookies[i].getName().equals(Utils.cookieName)) {
                i++;
            }

            if(i == cookies.length) {
                // There is no cookie with correct name, so start new session
                state = SessionState.newSession(Utils.defaultMsg);
            } else {

                sessionCookie = cookies[i];

                //retrive session state
                //state = SessionState.removeSession(Utils.getCookieSessionId(sessionCookie));
                state = SessionState.readSession(Utils.getCookieSessionId(sessionCookie),
                                                Utils.getCookieVersion(sessionCookie),
                                                Utils.getCookieIps(sessionCookie));

                // check that session retrieved is actually in state table
                // if it is not, assume session has expired, but user's cookies haven't
                // been cleared (can be triggered by restarting the Tomcat instance but not
                // restarting/clearing the browser's cookies)
                if(null == state) {
                    out.println(Utils.genHTML(title, "Session Has Expired", ""));
                    // we don't have this session state, so remove user's cookie
                    sessionCookie.setMaxAge(0);
                    response.addCookie(sessionCookie);
                    return;
                }

                cookieips = Utils.getCookieIps(sessionCookie);
                state.incrementVersion();
            }
        }



        // Add session to session table
        String[] hostips = SessionState.writeSession(state, cookieips);

        // Send user a new cookie
        sessionCookie = new Cookie( Utils.cookieName, 
                Utils.formCookieValue(state.getSessionId(), state.getVersion(), hostips));
        sessionCookie.setMaxAge(Utils.cookieMaxAge);
        response.addCookie(sessionCookie);

        // Display form
        String extra = Utils.entryForm + "\n" 
            + "<p>Session Cookie: " + Utils.cookieString(sessionCookie) + "</p>";
        out.println(Utils.genHTML(title, state.getMessage(), extra));
    }


}
