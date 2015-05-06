package servlet;

import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;

public class RPCListener extends Thread {
    private DatagramSocket rpcSocket = null;
    private static boolean listenerRunning = false;

    public void run() {
        try {
            rpcSocket = new DatagramSocket(NetUtils.PROJ1BRPC_PORT);

            // Loop indefinitely
            while(true) {
                byte[] recvBuffer = new byte[512];
                DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);
                
                // Wait for a reqest to arrive
                rpcSocket.receive(recvPacket);

                // Extract basic information about the recieved request
                int len_recv = recvPacket.getLength();
                InetAddress retAddr = recvPacket.getAddress();
                int retPort = recvPacket.getPort();
                View.addAddr(retAddr);

                // Extract the request components from the packet
                ByteBuffer buff = ByteBuffer.wrap(recvBuffer, 0, len_recv);
                int callid = buff.getInt();
                short opcode = buff.getShort();
                byte[] args = new byte[len_recv - (4 + 2)];
                buff.get(args, 0, args.length);

                // handle the request and get the response
                byte[] res = handleRequest(opcode, args);

                // Package up the response with the call id and REPLY op code
                buff = ByteBuffer.allocate(4 + res.length);
                buff.putInt(callid);
                buff.put(res);
                
                // Send the results to the address and port we got the request from
                DatagramPacket sendPacket = 
                    new DatagramPacket(buff.array(), buff.capacity(), retAddr, retPort);
                rpcSocket.send(sendPacket);
            }
        } catch (Exception e) {
            System.out.println("Exception in RPCListener: " + e.toString());
            e.printStackTrace();
        } finally {
            if (null != rpcSocket && rpcSocket.isConnected()) {
                rpcSocket.close();
            }
        }
    }


    public byte[] handleRequest(short op, byte[] args) {
        switch(op) {
            case NetUtils.SESSION_READ:
                return sessionRead(args);
            case NetUtils.SESSION_WRITE:
                return sessionWrite(args);
            case NetUtils.GET_VIEW:
                return getView();
            default:
                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.putShort(NetUtils.NO_REPLY);
                return bb.array();
        }
    }


    private byte[] sessionRead(byte[] args) {
        byte[] retArgs;
        short op;
        String[] sessionInfo = (new String(args)).split("_");
        if(sessionInfo.length < 2) {
            retArgs = (sessionInfo[0] + "_-1_NULL").getBytes();
            op = NetUtils.NO_REPLY;
        } else {
            String sessionid = sessionInfo[0];
            int version = -1;
            try {
                version = Integer.parseInt(sessionInfo[1]);
            } catch (NumberFormatException nfe) {
                version = -1;
            }

            SessionState ss = SessionState.getSession(sessionid, version);
            if (null == ss || -1 == version) {
                retArgs = (sessionInfo[0] + "_-1_NULL").getBytes();
                op = NetUtils.NO_REPLY;
            } else {
                retArgs = ss.toBytes();
                op = NetUtils.REPLY;
            }
        }
        ByteBuffer retBuf = ByteBuffer.allocate(2 + retArgs.length);
        retBuf.putShort(op); 
        retBuf.put(retArgs);
        return retBuf.array();
    }

    private byte[] sessionWrite(byte[] args) {
        byte[] serverip = NetUtils.getIP().getBytes();
        ByteBuffer retBuf = ByteBuffer.allocate(2 + serverip.length);
        String[] sesInfo = (new String(args)).split("_", 3);
        if(sesInfo.length < 3) {
            retBuf.putShort(NetUtils.NO_REPLY);
        } else {
            SessionState ss = 
                new SessionState(sesInfo[0], Integer.parseInt(sesInfo[1]), sesInfo[2]);
            SessionState.addSession(ss, Utils.remoteSessionExpTime);
            retBuf.putShort(NetUtils.REPLY);
        }
        retBuf.put(serverip);
        return retBuf.array();
    }

    private byte[] getView() {
        byte[] view = View.getString().getBytes();
        ByteBuffer retBuf = ByteBuffer.allocate(2 + view.length);
        retBuf.putShort(NetUtils.REPLY);
        retBuf.put(view);
        return retBuf.array();
    }
}
