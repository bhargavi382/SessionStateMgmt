package servlet;

import java.net.InetAddress;

import java.net.UnknownHostException;
import java.util.Arrays;

import javax.servlet.http.*;

public class Utils {
    protected static final int cookieMaxAge = 20; //Max age (in seconds) for a cookie
    protected static final int sessionExpTime = 25*1000; // session expiration time (in msecs)
    protected static final int remoteSessionExpTime = sessionExpTime + 5*1000;
    protected static final String defaultMsg = "Hello Generic User!";
    protected static final String cookieName = "CS5300PROJ1SESSION";

    // HTML string of the form(s) ofr th servlets
    protected static String entryForm = 
        "<Form METHOD=\"POST\" ACTION=\"ReplaceServlet\">\n" +
            "<INPUT TYPE=\"SUBMIT\" VALUE=\"Replace\">" + 
            "<INPUT TYPE=\"TEXT\" NAME=\"replaceName\" VALUE=\"\"><BR>\n" +
        "</FORM>\n" +
        "<Form METHOD=\"POST\" ACTION=\"RefreshServlet\">\n" +
            "<INPUT TYPE=\"SUBMIT\" VALUE=\"Refresh\">\n" +
        "</FORM>\n" +
        "<Form METHOD=\"POST\" ACTION=\"LogOutServlet\">\n" +
            "<INPUT TYPE=\"SUBMIT\" VALUE=\"Log Out\">\n" +
        "</FORM>\n";

    /**
     * Given the title, message, and any other HTML to display, construct full HTML page.
     *
     * @param title The page title
     * @param msg The message to display
     * @param other Any other HTML (e.g. form, errors)
     * @return The complete HTML page as a string
     */
    public static String genHTML(String title, String msg, String other) {
        String docType = 
            "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 " +
            "Transitional//EN\">\n";
        String head = 
            "<HTML>\n" +
            "<HEAD><TITLE>" + title + "</TITLE></HEAD>\n" +
            "<BODY BGCOLOR=\"#FDF5E6\">\n";
        String message = "<H1 ALIGN=\"CENTER\">" + msg + "</H1>\n";
        String ipstuff = 
            "<p>Handling Server: " + NetUtils.getIP() + "</p>" + 
            "<p>View: " + View.getString() + "</p>";
        String foot = "</BODY></HTML>";
        return (docType + head + message + other + ipstuff + foot);
    }


    /**************************
     * 
     * Functions Over Cookies
     *
     **************************/


    /** Given a cookie, return a string representation including name and value.
     *
     * @param cookie The Cookie object to represent
     * @return String representation that includes name and value.
     */
    public static String cookieString(Cookie cookie) {
        return (cookie.getName() + " || " + cookie.getValue());
    }

    /** Given a Cookie (assumed to be in standard format for this codebase),
     *  parse out and return the session id.
     *
     *  @param cookie The Cookie to retrieve the session id from
     *  @return The session id for this cookie, as an integer.
     */
    public static String getCookieSessionId(Cookie cookie) {
        return cookie.getValue().split("_")[0];
    }

    /** Given a sessionid, version number, and array of server ids, construct a String
     *  representation of this information to be placed in a cookie.
     *
     *  @param sessionid The id of the session the cookie will be associated with.
     *  @param version The version number of the cookie.
     *  @param serverids A list of server ids where this sessions information is stored.
     *  @return The constructed string.
     */
    public static String formCookieValue(String sessionid, int version, String[] serverids) {
       String servers = "";
       for(String id : serverids) {
           servers += "_" + id;
       }
       return (sessionid + "_" + version + servers);
    }

    /** Given a cookie value (as set by formCookieValue()), update the version number
     *  and return the new string.
     *
     *  @param cookieValue The value (String) of the cookie to update the version number of
     *  @param version The new version number for the cookie.
     *  @return The updated value (String).
     */
    public static String setCookieVersion(String cookieValue, int version) {
        String[] splitcookie = cookieValue.split("_");
        splitcookie[1] = "" + version;
        String newval = splitcookie[0];
        for (int j = 1; j < splitcookie.length; j++) {
            newval += "_" + splitcookie[j];
        }
        return newval;
    }

    /** Given a cookie, extract the version number from it's value
     *
     * @param cookie The cookie to extract the version from
     * @return The version number of the cookie
     */
    public static int getCookieVersion(Cookie cookie) {
        return Integer.parseInt(cookie.getValue().split("_", 3)[1]);
    }


    /** Given a cookie, extract the IP addresses of the servers that hold this cookie's session.
     *
     * @param cookie The cookie to extract the server IPs from
     * @return The list of server IPs (as a String array)
     */
    public static String[] getCookieIps(Cookie cookie) {
        String servers_str = cookie.getValue().split("_", 3)[2];
        return servers_str.split("_");
    }
}
