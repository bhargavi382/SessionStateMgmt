package servlet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class NetUtils {
    protected static final String ip_pattern = "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$";
    protected static final String nullIP = "0.0.0.0";

    protected static Integer callID = 0;
    protected static int MAX_PACKET_LEN = 512;
    protected static final int PROJ1BRPC_PORT = 5300;
    protected static Boolean initRun = false;

    private static String serverip = null;
    private static RPCListener clientListener = new RPCListener();

    public static final short SESSION_READ = 0;
    public static final short SESSION_WRITE = 1;
    public static final short GET_VIEW = 2;
    public static final short REPLY = 3;
    public static final short NO_REPLY = 4;

    
    /** Request a session (given by session id) from a selection of servers.
     *
     *  @param sessionid The ID of the session requested
     *  @param version The version of the session we are looking for
     *  @param addrs The IP addresses of the servers we are requesting from
     *  @return The SessionState object representing the requested session
     */
    public static SessionState sessionReadClient(String sessionid, int version, InetAddress[] addrs) {
        //generate request args
        byte[] args = (sessionid + "_" + version).getBytes();

        // send request and get reply
        byte[] reply = sendRequest(SESSION_READ, args, addrs);

        // convert reply to session state object
        return SessionState.fromBytes(reply);
    }

    /** Request that a selection of servers store (write) the given session.
     *  Servers are asked until the list provided is exhausted or we get a successful reply
     *
     * @param session The SessionState object representing the session to be written
     * @param addrs List of IP address of servers to ask to write the session
     * @return String ip address of write server if there was a successful write, null IP otherwise.
     */
    public static String sessionWriteClient(SessionState session, InetAddress[] addrs) {
        //generate request args
        byte[] args = session.toBytes();

        // send request and get reply
        byte[] reply = sendRequest(SESSION_WRITE, args, addrs);

        if(null != reply) {
            return new String(reply);
        } else {
            return nullIP;
        }
    }

    /** Send a request to a selection of servers.
     *  Only one server is required to give a successful reply.
     *
     *  @param opCode The op code for then request we are making
     *  @param args The arguments for the operation as a byte[]
     *  @param addrs Lis of IP address of servers to send the request to
     *  @return The arguments of a successful reply (if there was one), and null otherwise
     */
    public static byte[] sendRequest(short opCode, byte[] args, InetAddress[] addrs) {
        DatagramSocket rpcSocket = null;
        Integer cid;
        ByteBuffer sendPacketBuff, recvPacketBuff = null;
        int recvCID;
        short recvCode;
        DatagramPacket recvPacket, sendPacket = null;
        byte[] inbuff = new byte[MAX_PACKET_LEN];
        byte[] replybuff = null;

        // Make sure to truncate args if larger than we can send
        int len_args_sent = Math.min(args.length, MAX_PACKET_LEN - (4 + 2));

        try {
            // Init new socket and set 2 second timeout on recieves
            rpcSocket = new DatagramSocket();
            rpcSocket.setSoTimeout(2000);

            // get new (unique) call id
            synchronized (callID) {
                cid = callID++;
            }

            // initialize and populate new packet
            sendPacketBuff = ByteBuffer.allocate(4 + 2 + args.length);
            sendPacketBuff.putInt(cid);
            sendPacketBuff.putShort(opCode);
            sendPacketBuff.put(args, 0, len_args_sent);
            sendPacket = 
                new DatagramPacket(sendPacketBuff.array(), sendPacketBuff.capacity());

            // Set correct port
            sendPacket.setPort(PROJ1BRPC_PORT);
            
            // iterate through all possible addresses
            for (InetAddress ip : addrs) {
                sendPacket.setAddress(ip);
                rpcSocket.send(sendPacket);

                recvPacket = new DatagramPacket(inbuff, inbuff.length);
                
                // wait for a response until we get one with the correct call id
                // or the socket times out
                try {
                    do {
                        recvPacket.setLength(inbuff.length);

                        rpcSocket.receive(recvPacket);

                        recvPacketBuff = ByteBuffer.wrap(inbuff);
                        recvCID = recvPacketBuff.getInt();
                        recvCode = recvPacketBuff.getShort();
                    } while (recvCID != cid && recvCode != REPLY);
                } catch (SocketTimeoutException ste) {
                    //reset recvPacket and the buffer if we time out
                    recvPacketBuff = null;
                    recvPacket = null;
                    //remove the address from our view
                    System.out.println("Removing ip: " + ip);
                    View.removeAddr(ip);
                    //move onto next address
                    continue;
                }

                // Got a reply, but not of success
                if(NO_REPLY == recvCode) {
                    continue;
                }

                // extract packet contents we care about and return to caller
                if (recvPacket.getLength() - (4 + 2) > 0) {
                    replybuff = new byte[recvPacket.getLength() - (4 + 2)];
                    recvPacketBuff.get(replybuff, 0, replybuff.length);
                } else {
                    replybuff = new byte[1];
                }
            }
        } catch (SocketException se) {
            //TODO: Handle differently
            System.out.println(se.getMessage());
        } catch (IOException ioe) {
            //TODO: Handle differently
            System.out.println(ioe.getMessage());
        } finally {
            // Always close the socket
            if (null != rpcSocket && rpcSocket.isConnected()) {
                rpcSocket.close();
            }
        }
        //Just return null if we get an exception
        //TODO: Is this wanted behavior?
        return replybuff;
    }


    /** Get the IP address of the machine this function runs on.
     *
     * @return The non-loopback IP address of this machine, if one exists, and localhost otherwise
     */
    private static String retrieveIP() {
        try {
            Runtime rt= Runtime.getRuntime();
            //Process pro= rt.exec("curl http://169.254.169.254/latest/meta-data/public-ipv4");
            Process pro= rt.exec("/opt/aws/bin/ec2-metadata --public-ipv4");
            int code= pro.waitFor();
            if(code == 0)
            {
                BufferedReader r = new BufferedReader(new InputStreamReader(pro.getInputStream()));
                String ip = r.readLine();
                // ip should be of form "public ipv4: ww.xx.yy.zz"
                String patternStr=".*: (\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})$";
                Pattern p = Pattern.compile(patternStr);
                Matcher m = p.matcher(ip);
                //System.err.println(s);
                if(m.find()){
                    return m.group(1);
                }
            }
            return "127.0.0.1";
        } catch (IOException ioe) {
            return "127.0.0.1";
        } catch (InterruptedException ie) {
            return "127.0.0.1";
        }
    }
    
//    private static String retrieveIP() {
//        try {
//            Enumeration<NetworkInterface> eni =  NetworkInterface.getNetworkInterfaces();
//            for (NetworkInterface ni : Collections.list(eni)) {
//                if(ni.isLoopback() || !ni.isUp()) {
//                    continue;
//                }
//                Enumeration<InetAddress> eia = ni.getInetAddresses();
//                for (InetAddress ia : Collections.list(eia)) {
//                    String ip = ia.getHostAddress();
//                    if(Pattern.matches(ip_pattern, ip)) {
//                        return ip;
//                    }
//                }
//            }
//            return InetAddress.getLocalHost().getHostAddress();
//        } catch (Exception e) {
//            System.out.println(e.getMessage());
//            return "127.0.0.1";
//        }
//    }

    /** Retrive the ip address of this instance (cached if possible, retrieve if not).
     */
    public static String getIP() {
        if(null == serverip) {
            serverip = retrieveIP();
        }
        return serverip;
    }


    /** Start the RPCListener thread for this instance if it is not already started.
     */
    public static void startRPCListener() {
        if(!clientListener.isAlive()) {
            clientListener.setDaemon(true);
            clientListener.start();
        }
    }

    /** Run initializations for starting an instance.
     *
     *  Including initializing the garbage collector for the SessionState, the RPCListener,
     *  and bootstrapping an instance's view.
     */
    public static synchronized void initThreads() {
        if(!initRun) {
            SessionState.startStateGC();
            NetUtils.getIP();
            NetUtils.startRPCListener();
    
            //boostrap view
            View.bootstrapView();
            View.startViewWatcher();

            initRun = true;
        }
    }
}
