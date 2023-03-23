import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;


/*
CS258: Computer Communication Systems
Submission to: Prof. Navrati Saxena

Team members:
1. Akshay Sunil Gurnaney
2. Sumeet Arvind Ghegade
 */

public class Server {

    //Socket stream variables
    static volatile DataInputStream in;
    static volatile DataOutputStream dos;

    //Initialize data
    static volatile int receivedSeqNumber = -1;
    static boolean transmissionComplete = false;

    //Variables to calculate goodput
    static float allPacketsReceivedCounter = 0;
    static float goodPacketsCounter = 0;
    static int expectedPacket = 0;
    static float goodputSum = 0;
    static int goodputCount = 0;

    public static void main(String[] args) throws IOException {
        //Socket configuration
        int port = 5000;
        ServerSocket server = new ServerSocket(port);
        System.out.println("Server started");

        System.out.println("Waiting for the client ...");
        Socket socket = server.accept();
        System.out.println("Client accepted");

        //read data from client
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        //outputstream fot sending ACKs
        dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        //store total packets received
        int totalPacketsReceived = 0;

        //Run thread to store logs to plot graphs
        DataLog log = new DataLog();
        Thread t1 = new Thread(log);
        t1.start();

        //Wait for handshake message
        waitForHandshake();

        //Start accepting data
        while (true) {
            try {
                //Read Data
                receivedSeqNumber = in.readInt();
                //Increment received packets counter
                allPacketsReceivedCounter++;

                //Calculate goodput every 1000 packets
                calculategoodput();

                //End loop if all packets received
                if(receivedSeqNumber == -1)
                    break;

                //Send ACKS to client
                System.out.println("Received: " + receivedSeqNumber);
                dos.writeInt(receivedSeqNumber);
                dos.flush();

                //Increment received packets number
                totalPacketsReceived++;

            } catch (IOException i) {
                i.printStackTrace();
            }
        }
        transmissionComplete = true;

        //Print results
        System.out.println("--------------------RESULTS-------------------------");
        System.out.println("----------------------------------------------------");
        System.out.println("Receiver IP Address: " + InetAddress.getLocalHost());
        System.out.println("Total correct packets received: " + goodPacketsCounter);
        System.out.println("Total packets received: " + totalPacketsReceived);

        //Calculate average goodput
        float avgGP = (goodputSum/goodputCount) * 100f;
        System.out.println("Goodput: " + avgGP);
    }

    //Wait for client to send "Network" to start accepting data
    public static void waitForHandshake() throws IOException {
        while (true) {
            String handshake = in.readUTF();
            System.out.println(handshake);
            if (handshake.equals("Network")) {
                dos.writeUTF("Success");
                System.out.println(handshake);
                break;
            }
        }
        return;
    }

    //Calculate goodput
    public static void calculategoodput() {
        //Logic to check if packet is a good packet and increment good packets counter
        if(receivedSeqNumber == expectedPacket)
        {
            expectedPacket++;
            expectedPacket = expectedPacket % 65535;
            goodPacketsCounter++;
        }
        //calculate sum and count for average goodput
        if(allPacketsReceivedCounter % 1000 == 0) {
            float currGoodPut = goodPacketsCounter/allPacketsReceivedCounter;
            goodputSum += currGoodPut;
            goodputCount++;
            allPacketsReceivedCounter = 0;
            goodPacketsCounter = 0;
        }
        return;
    }

    //Code to log data for plotting graph
    static class DataLog implements Runnable {
        @Override
        public void run() {
            FileWriter myWriter1;
            try {
                myWriter1 = new FileWriter("ReceivedSeqNum.txt");
                int i = 1;
                while (!transmissionComplete) {
                    if(receivedSeqNumber != -1)
                        myWriter1.write(i + " " + receivedSeqNumber + "\n");
                    i++;
                    Thread.sleep(1000);
                }
                myWriter1.close();
                return;
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

}
