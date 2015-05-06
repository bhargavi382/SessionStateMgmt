package servlet;

public class StateTest {

	public static void main(String[] args) {
        viewTest();
        //stateTest();
	}


    public static void viewTest() {
        View.addAddr("127.0.0.1");
        View.addAddr("10.33.23.237");
        View.addAddr("10.32.215.159");
        View.addAddr("10.33.23.237");
        System.out.println(View.getString());
    }

    public static void stateTest() {
        SessionState ss = new SessionState(120, "10.32.215.123", 24, "This is a Message from");

        byte[] ss_bytes = ss.toBytes();
        

        SessionState ss2 = SessionState.fromBytes(ss_bytes);
        System.out.println(
                "SessionId: " + ss2.getSessionId() + "\n" + 
                "Version: " + ss2.getVersion() + "\n" + 
                "Message: " + ss2.getMessage());
    }

}
