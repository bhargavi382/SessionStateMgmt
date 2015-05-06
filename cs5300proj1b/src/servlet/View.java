package servlet;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import com.amazonaws.services.simpledb.model.*;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;

public class View {
	private static HashSet<InetAddress> viewset = new HashSet<InetAddress>();
    private static AmazonSimpleDBClient client = new AmazonSimpleDBClient();
    private static final int MAX_VIEW_SIZE = 5;
    private static ViewWatcher viewwatcher = new ViewWatcher();

    /** Take the view, and choose a random subset of (max) size MAX_VIEW_SIZE to maintain, and discard the others.
     */
    public static synchronized void reduceSetSize() {
        List<InetAddress> tmp = new LinkedList<InetAddress>(viewset);        
        Collections.shuffle(tmp);
        int newsize = Math.min(tmp.size(), MAX_VIEW_SIZE);
        viewset = new HashSet<InetAddress>(tmp.subList(0, newsize));
    }

    /** Retrieve this instance's view as an array.
     *  @return The view as an array of InetAddress
     */
    public static synchronized InetAddress[] getView() {
        InetAddress[] arrayview = new InetAddress[viewset.size()];
        viewset.toArray(arrayview);
        return arrayview;
    }

    /** Add an new IP address to the view.
     *
     * @param addr An InetAddress object representing the address we want to add
     * @return True if the add succeeded, false otherwise
     */
    public static synchronized boolean addAddr(InetAddress addr) {
        if(addr.getHostAddress().equals("0.0.0.0") ||
                addr.getHostAddress().equals(NetUtils.getIP())) {
            return true;
        }
        return viewset.add(addr);
    }

    /** Add a new IP address to the view.
     *
     * @param addr The address (represented as a string) to add
     * @return True if the add succeeded, false otherwise
     */
    public static synchronized boolean addAddr(String addr) {
        try {
            InetAddress newAddr = InetAddress.getByName(addr);
            return addAddr(newAddr);
        } catch (UnknownHostException uhe) {
            System.out.println("FAILED TO ADD ADDR:\n\t" + uhe.getMessage());
            return false;
        }
    }
    
    /** Remove an IP address from the view.
     *
     * @param addr The IP address (as an InetAddress object) to remove from the view.
     * @return True if the remove succeeds, false otherwise (including addr is already not in view)
     */
    public static synchronized boolean removeAddr(InetAddress addr) {
        return viewset.remove(addr);
    }

    /** Get return the view as an "_" separated String.
     *
     * @return An "_" separated string, where each entry is the string representation of an
     *          IP address contained in this view.
     */
    public static String getString() {
        InetAddress[] addrs = getView();

        if(0 == addrs.length) {
            return "0.0.0.0";
        }

        String view = ""  + addrs[0].getHostAddress();
        for (int i = 1; i < addrs.length; i++) {
            view = view + "_" + addrs[i].getHostAddress();
        }
        return view;
    }

    /** Given an "_" separated string representation of a view, merge the views.
     *
     * @param String addrs The "_" separated string of address (as produced by getString())
     */
    public static void mergeView(String addrs) {
        String[] view_strs = addrs.split("_");
        for (String ip : view_strs) {
            addAddr(ip);
        }
    }

    /** Update our view from the bootstrap, then take a random subset to write back to the domain.
     */
    public static void updateBootstrapView() {
        bootstrapView();
        reduceSetSize();

        InetAddress[] view = getView();
        List<ReplaceableItem> new_view = new LinkedList<ReplaceableItem>();

        for (int i = 0; i<view.length; i++) {
            new_view.add(
                (new ReplaceableItem("server" + i)).withAttributes(
                    new ReplaceableAttribute("ip", view[i].getHostAddress(), true)));
        }
        new_view.add(
                (new ReplaceableItem("server" + view.length)).withAttributes(
                    new ReplaceableAttribute("ip", NetUtils.getIP(), true)));

        for (ReplaceableItem item : new_view) {
            System.out.println(item);
        }

        client.batchPutAttributes(new BatchPutAttributesRequest("proj1bview", new_view));
    }


    /** "Gossip" with a random server in our view, and merge their view.
     */
    public static void gossipViewUpdate(Random rand) {
        InetAddress[] view = getView();

        if(view.length == 0) {
            return;
        }

        int view_idx = rand.nextInt(view.length);

        byte[] args = NetUtils.getIP().getBytes();

        byte[] reply = NetUtils.sendRequest(
                NetUtils.GET_VIEW, args, new InetAddress[]{view[view_idx]});
        
        if (null != reply && reply.length > 1) {
            mergeView(new String(reply));
            System.out.println(new String(reply));
        }
    }

    /** Get the boostrap view and add all of the items into our view.
     */
    public static void bootstrapView() {
        SelectResult result=client.select(new SelectRequest("select ip from proj1bview"));
        List<Item> items = result.getItems();
        List<Attribute> attrs;
        for (Item item : items) {
            attrs = item.getAttributes();
            addAddr(attrs.get(0).getValue());
        }
    }


    /** Start the thread to periodically update [from] the bootstrap view.
     */
    public static void startViewWatcher() {
        synchronized(viewwatcher) {
            if(!viewwatcher.isAlive()) {
                viewwatcher.setDaemon(true);
                viewwatcher.start();
            }
        }
    }

    /** Threads for the purpose of periodically updating [from] the bootstrap view
     */
    private static class ViewWatcher extends Thread {
        @Override
        public void run() {
            int sleeptime = 5000;
            Random rand = new Random();

            while(true) {
                updateBootstrapView();
                
                System.out.println("Bootstrap Update");

                try {
                    Thread.sleep(sleeptime/2 + rand.nextInt(sleeptime));
                } catch (Exception e) {
                    //TODO: Handle?
                }

                gossipViewUpdate(rand);
                
                System.out.println("Gossip Update");
                
                try {
                    Thread.sleep(sleeptime/2 + rand.nextInt(sleeptime));
                } catch (Exception e) {
                    //TODO: Handle?
                }
            }
        }
    }
}
