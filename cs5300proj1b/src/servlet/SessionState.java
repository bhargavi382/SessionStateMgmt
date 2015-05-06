package servlet;

import java.util.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class SessionState {
    
    // The session table itself
    private static HashMap<String, SessionState> statemap = 
            new HashMap<String, SessionState>();
    private static Integer globalSessionNo = 0; // The next session id to be assigned
    private static StateWatcher stateGC = new StateWatcher(); // The garbage collection thread

    private String sessionid;  // The id of this session
    private int version;    // The version number of this session
    private String message; // The message for this session
    private Date expDate;   // The expiration time of this session

    public SessionState(int sessionNo, String serverid, int version, String msg) {
        this.sessionid = sessionNo + "/" + serverid;
        this.version = version;
        this.message = msg;
    }

    public SessionState(String sessionid, int version, String msg) {
        this.sessionid = sessionid;
        this.version = version;
        this.message = msg;
    }


    /** Retrieve the id for this session.
     * @return The session id as a String
     */
    public String getSessionId() {
        return this.sessionid;
    }

    /** Retrieve just the session number for this session.
     * @return The session number
     */
    public int getSessionNo() {
        String[] sid = sessionid.split("/");
        return Integer.parseInt(sid[0]);
    }

    /** Retrieve just the ID (IP) of the server that created this session.
     * @return The server ID (currently the IP addr)
     */
    public String getServerID() {
        String[] sid = sessionid.split("/");
        return sid[1];
    }

    /** Retrieve the version number for this session.
     * @return The version number as an int
     */
    public int getVersion() {
        return this.version;
    }

    /** Increment the version number of this session by 1.
     */
    public void incrementVersion() {
        this.version += 1;
    }

    /** Retrieve the message stored in this session
     *  @return This session's message as a String
     */
    public String getMessage() {
        return this.message;
    }

    /** Change this session's message.
     * @param newMsg The new message for this session
     */
    public void setMessage(String newMsg) {
        this.message = newMsg;
    }

    /** Retrieve the expiration date for this session.
     *  @return The expiration time as a Date object.
     */
    public Date getExpDate() {
        return this.expDate;
    }

    
    /** Produce a byte[] from a SessionState object that can be recreated using fromBytes().
     *  @return byte[] reresentation of this object
     */
    public byte[] toBytes()
    {
        String rep = sessionid + "_" + version + "_" + message;
        return rep.getBytes();
    }

    /** Recreate a SessionState object from a byte[] as produced by toBytes().
     * @return The SessionState object stored in the byte[]
     */
    public static SessionState fromBytes(byte[] bytes)
    {
        String[] sessionInfo = (new String(bytes)).split("_", 3);
        String[] sesid = sessionInfo[0].split("/");
        
        return new SessionState(
                Integer.parseInt(sesid[0]), 
                sesid[1],
                Integer.parseInt(sessionInfo[1]),
                sessionInfo[2]);
    }

    /** Adds a session to the session table.
     *
     * @param session The session to be added - expDate will be overwritten
     * @param expLength The length of time after which this session should expire
     */
    public static synchronized void addSession(SessionState session, int expLength) {
        session.expDate = new Date((new Date()).getTime() + expLength);
        statemap.put(session.getSessionId(), session);
    }

    /** Create a brand new session storing the given method.
     *  Gives the session version number 1, but does not put in in the session table.
     *
     *  @param msg The message to store in the session.
     *  @return The newly created SessionState object
     */
    public static SessionState newSession(String msg) {
        int newSessionNo = 0;
        synchronized(globalSessionNo) {
            newSessionNo = globalSessionNo++;
        }
        return new SessionState(newSessionNo, NetUtils.getIP(), 1, msg);
    }

    /** Gets and removes the requested session state from the table (if it exists).
     *
     * @param sessionid The id of the sesssion to be retrieved
     * @return The corresponding SessionState object, or null if the key does not exist.
     */
    public static synchronized SessionState removeSession(String sessionid) {
        return statemap.remove(sessionid);
    }
    public static synchronized SessionState removeSession(String sessionid, int version){
        SessionState session = statemap.remove(sessionid);
        if(null != session) {
            if(session.getVersion() != version) {
                statemap.put(session.getSessionId(), session);
                session = null;
            }
        }
        return session;
    }

    /** Gets the requested session state from the table (if it exists).
     *
     * @param sessionid The id of the sesssion to be retrieved
     * @return The corresponding SessionState object, or null if the key does not exist.
     */
    public static synchronized SessionState getSession(String sessionid) {
        return statemap.get(sessionid);
    }
    public static synchronized SessionState getSession(String sessionid, int version){
        SessionState session = statemap.get(sessionid);
        if(null != session) {
            if(session.getVersion() != version) {
                session = null;
            }
        }
        return session;
    }

    /** Get a list of session ids for sessions currently stored in the table.
     *
     * @return An array of Longs containing the currently stored session ids.
     */
    public static synchronized String[] getSessionIds() {
        String[] ids = new String[statemap.size()];
        statemap.keySet().toArray(ids);
        return ids;
    }


    /** Try to read a session identified by the given session id and version number.
     *
     *  @param sessionid The string sessionid of the session to read
     *  @param version The version number of the session to try to read
     *  @param serverips A list of server ip addresses to attempt to read the session from
     *  @return The retrieved session (as a SessionState object) or null if it is not found
     */
    public static SessionState readSession(String sessionid, int version, String[] serverips) {
        List<String> str_ips = Arrays.asList(serverips);
        if(str_ips.contains(NetUtils.getIP())) {
            return SessionState.removeSession(sessionid);
        } else {
            ArrayList<InetAddress> ips = new ArrayList<InetAddress>();
            for (String ip : str_ips) {
                if (ip.equals(NetUtils.nullIP)) {
                    continue;
                }
                try {
                    ips.add(InetAddress.getByName(ip));
                } catch (UnknownHostException uhe) {
                    System.out.println("*** EXCEPTION: " + uhe.getMessage());
                    System.out.println(uhe.getStackTrace());
                }
            }

            InetAddress[] iparray = new InetAddress[ips.size()];
            ips.toArray(iparray);
            return NetUtils.sessionReadClient(sessionid, version, iparray);
        }
    }

    /** Try to write a session.
     *
     * @param session The session to be written.
     * @param oldips An optional list of IP adresses to try to write to before the other
     *                  servers in our view.
     * @return An array of the IP addresses that the session was written to (one will be this
     *          instance's address, the other will be the null address (0.0.0.0) if no other server
     *          was successfully contacted.
     */
    public static String[] writeSession(SessionState session, String[] oldips) {
        //TODO: make adding IPs from the cookie more efficient
        ArrayList<InetAddress> writeIP = new ArrayList<InetAddress>();
        if (null != oldips && !oldips.equals("")) {
            for (String ip : oldips) {
                if (ip.equals(NetUtils.getIP()) || ip.equals(NetUtils.nullIP)) {
                    continue;
                }

                try {
                    writeIP.add(InetAddress.getByName(ip));
                
                    } catch (UnknownHostException uhe) {
                    System.out.println(uhe.getMessage());
                }
            }
        }
        //Don't want duplicate IPs in the list
        for(InetAddress ip : View.getView()) {
            if (!writeIP.contains(ip)) {
                writeIP.add(ip);
            }
        }

        InetAddress[] iparray = new InetAddress[writeIP.size()];
        writeIP.toArray(iparray);
        String ip = NetUtils.sessionWriteClient(session, iparray);
        
        SessionState.addSession(session, Utils.sessionExpTime);

        return new String[]{NetUtils.getIP(), ip};
    }


    /** Start the Garbage Collection thread if it is not already started.
     *  The thread is set as a daemon thread so it will not hold up the VM from exiting
     *  (we do not need to worry about incomplete deletes since all data is in memory)
     */
    public static void startStateGC() {
        synchronized (stateGC) {
            if(!stateGC.isAlive()) {
                stateGC.setDaemon(true);
                stateGC.start();
            }
        }
    }


    /** Threads for the purpose of garbage collecting from the session state table.
     */
    private static class StateWatcher extends Thread {
        @Override
        public void run() {
            SessionState state = null;
            Date curTime = null;

            while(true) {
                // get the current time
                curTime = new Date();

                // retrieve all session ids
                String[] sessionids = SessionState.getSessionIds();

                // iterate over all retrieved ids, and remove any that have expired
                for(String id : sessionids) {
                    state = SessionState.getSession(id);

                    if(null != state && curTime.after(state.expDate)) {
                        SessionState.removeSession(id);
                    }
                }

                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    //TODO: Handle?
                }
            }
        }
    }
}
