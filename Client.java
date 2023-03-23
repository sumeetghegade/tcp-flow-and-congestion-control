import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/*
CS258: Computer Communication Systems
Submission to: Prof. Navrati Saxena

Team members:
1. Akshay Sunil Gurnaney
2. Sumeet Arvind Ghegade
 */

class Client {

    //Wrap around at 2^16:
    static final int WRAPAROUND = 65535;//65535;
    //Maximum window size:
    static final int MAX_WINDOW_SIZE = WRAPAROUND - 1;
    //Number of packets to send:
    static volatile int numPackets = 1000000;


    static final int totalPackets = numPackets;
    static int totalPacketsSent = 0;

    //Socket:
    static volatile BufferedReader br;
    static volatile DataOutputStream dos;
    static DataInputStream dis;

    //Number of retransmissions for each packet
    static HashMap<Integer, Integer> retransmissionTable = new HashMap<>();


    //Boolean to know if all packets sent from sender
    static volatile boolean allPacketsSent = false;

    //Save received acknowledgements
    static Set<Integer> acksReceived = new HashSet<>();

    //Initialize all variables
    static volatile int nextExpectedAck = 0;
    static volatile int windowStartPointer = 0;
    static volatile int windowSize = 1;
    static volatile boolean doubleWindowSize = true;
    static volatile int lastPacketFromPreviousWindowResize = 0;
    static volatile int currPacketNumber = 0;
    static volatile int currentDroppedPacket = -1;

    public static void main(String[] args) throws Exception {

        //Create client socket object
        Socket s = new Socket("10.0.0.48", 5000);

        //Socket output stream to send packets
        dos = new DataOutputStream(s.getOutputStream());

        //Buffered reader to store input stream
        br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        //Socket Input stream to accept ACKs
        dis = new DataInputStream(new BufferedInputStream(s.getInputStream()));

        //Start handshake
        try {
            dos.writeUTF("Network");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Sender class object - sends packets
        Sender sn = new Sender();
        //Receiver class objects - receives packets
        Receiver rc = new Receiver();
        //Logger class to store data for graphs
        DataLog dl = new DataLog();


        Thread SenderThread = new Thread(sn);
        Thread ReceiverThread = new Thread(rc);
        Thread DataLogThread = new Thread(dl);

        SenderThread.start();
        ReceiverThread.start();
        DataLogThread.start();


        // close connection.
        // dos.close();
        // br.close();
        // s.close();
    }


    /*
    Sender code:
     */
    static class Sender implements Runnable {

        static HashSet<Integer> packetsToDropSet1 = new HashSet<>();
        static HashSet<Integer> packetsToDropSet2 = new HashSet<>();
        static HashSet<Integer> packetsToDropSet3 = new HashSet<>();
        long timeout = 1000;
        HashSet<Integer> sentSet = new HashSet<>();
        //Array to store send time of each packet. Used to check timeout.
        Date[] packetSendTime = new Date[numPackets];

        //Count of retransmissions for each packet
        HashMap<Integer, Integer> retransmitCount = new HashMap<>();

        @Override
        public void run() {

            //Compute drops probabilistically
            computeDrops();

            //Start sending packets
            while (numPackets > 0) {
                while (currPacketNumber < (windowStartPointer + windowSize) && currPacketNumber < totalPackets) {

                    /* Switch case block to drop or send packet
                     * CASE 0: First transmission --> Drop packets from if present in packetsToDropSet1
                     * CASE 1: First retransmission --> Drop packets from if present in packetsToDropSet2
                     * CASE 2: Second retransmission --> Drop packets from if present in packetsToDropSet3
                     * CASE 3: Third retransmission --> No packets to drop. Send all packets.
                     */
                    switch (retransmitCount.getOrDefault(currPacketNumber, 0)) {
                        case 0:
                            if (packetsToDropSet1.contains(currPacketNumber)) {
                                System.out.println("(C) Dropping: " + currPacketNumber + " i.e Sequence No. " + (currPacketNumber % WRAPAROUND) + " for 1st time");
                                currentDroppedPacket = currPacketNumber % WRAPAROUND;
                                numPackets++;
                                retransmitCount.put(currPacketNumber, 1);
                            } else {
                                System.out.println("(C) Sending: " + currPacketNumber + " i.e Sequence No. " + (currPacketNumber % WRAPAROUND) + " for 1st time");
                                sendPacket();
                            }
                            break;
                        case 1:
                            if (packetsToDropSet2.contains(currPacketNumber)) {
                                System.out.println("(C) Dropping: " + currPacketNumber + " i.e Sequence No. " + (currPacketNumber % WRAPAROUND) + " for 2nd time");
                                currentDroppedPacket = currPacketNumber % WRAPAROUND;
                                numPackets++;
                                retransmitCount.put(currPacketNumber, 2);
                            } else {
                                System.out.println("(C) Sending: " + currPacketNumber + " i.e Sequence No. " + (currPacketNumber % WRAPAROUND) + " for 2nd time");
                                sendPacket();
                            }
                            break;
                        case 2:
                            if (packetsToDropSet3.contains(currPacketNumber)) {
                                System.out.println("(C) Dropping: " + currPacketNumber + " i.e Sequence No. " + (currPacketNumber % WRAPAROUND) + " for 3rd time");
                                currentDroppedPacket = currPacketNumber % WRAPAROUND;
                                numPackets++;
                                retransmitCount.put(currPacketNumber, 3);
                            } else {
                                System.out.println("(C) Sending: " + currPacketNumber + " i.e Sequence No. " + (currPacketNumber % WRAPAROUND) + " for 3rd time");
                                sendPacket();
                            }
                            break;
                        case 3:
                            System.out.println("(C) Sending: " + currPacketNumber + " i.e Sequence No. " + (currPacketNumber % WRAPAROUND));
                            sendPacket();
                            break;
                    }

                    //Save send time of the packet
                    packetSendTime[currPacketNumber] = new Date();

                    //Keep count of sent packets
                    if (!sentSet.contains(currPacketNumber)) {
                        numPackets--;
                    }
                    currPacketNumber++;

                    System.out.println("Value of numPackets : " + numPackets);
                    System.out.println("Total Packets Sent is : " + totalPacketsSent);
                    System.out.println("Current packet number : " + currPacketNumber);

                    //Break out of the loop if all packets have been sent
                    if (numPackets <= 0)
                        break;
                    if (currPacketNumber >= totalPackets)
                        break;

                    //Wait time added to accommodate network delay
                    try {
                        Thread.sleep(0, 500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                //Save next expected ack as it is shared between threads
                int currNextExpAck = nextExpectedAck;

                //Break out of the loop if all acks have been received
                if (currPacketNumber >= totalPackets && currNextExpAck >= totalPackets)
                    break;


                //If ACK not received for dropped packet, wait till its timeout
                waitTillTimeout(currNextExpAck);

                //Adjust window size
                adjustWindowSize(currNextExpAck);
                currPacketNumber = nextExpectedAck;

                System.out.println("current packet number : " + currPacketNumber);
                System.out.println("current numPackets : " + numPackets);
                System.out.println("current nextExpAck : " + nextExpectedAck);
            }

            //All packets sent
            allPacketsSent = true;
            //Print results
            printResults();
            //close output stream
            try {
                dos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        //Probabilistically compute drops
        public void computeDrops() {
            Random random = new Random();
            //Drop 1:
            for (int i = 0; i < numPackets; i++) {
                if (random.nextInt(99) == 0) {
                    packetsToDropSet1.add(i);
                }
            }
            //Drop 2:
            for (int i : packetsToDropSet1) {
                if (random.nextInt(99) == 0) {
                    packetsToDropSet2.add(i);
                }
            }
            //Drop 3:
            for (int i : packetsToDropSet2) {
                if (random.nextInt(99) == 0) {
                    packetsToDropSet3.add(i);
                }
            }
            return;
        }

        //Send packet and adjust sent packet numbers
        public void sendPacket() {
            try {
                dos.writeInt(currPacketNumber % WRAPAROUND);
                if (!sentSet.contains(currPacketNumber)) {
                    totalPacketsSent++;
                    numPackets--;
                    sentSet.add(currPacketNumber);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        //Wait for packets timeout before resending
        public void waitTillTimeout(int currNextExpAck) {
            long timeTillTimeout = (timeout - (new Date()).getTime() - packetSendTime[currNextExpAck].getTime());

            System.out.println("nextExpAck currently : " + currNextExpAck);
            System.out.println("failure at : " + currNextExpAck);
            System.out.println("timeout remaining : " + timeTillTimeout);

            //Wait for remaining timeout
            if (timeTillTimeout > 0) {
                try {
                    System.out.println("Waiting for timeout to end");
                    Thread.sleep(timeTillTimeout);
                    System.out.println("timeout ended");

                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }
            return;
        }

        //Adjust window size on failure
        public void adjustWindowSize(int currNextExpAck) {
            if (acksReceived.contains(currNextExpAck)) {
                // If the packet is received till timeout-> no problem and we can still move into the loop
                // CONTINUE
            } else {
                //ACK not received
                System.out.println("Ack still not received");

                //Reduce window size by half
                windowSize = (int) Math.ceil(windowSize / 2.0);

                //Limit window size to max window size
                windowSize = windowSize < MAX_WINDOW_SIZE ? windowSize : MAX_WINDOW_SIZE;
                System.out.println("Reducing windowSize to : " + windowSize + " current baseP : " + windowStartPointer);

                //Restart at window start
                windowStartPointer = nextExpectedAck;
                lastPacketFromPreviousWindowResize = windowStartPointer + windowSize - 1;

                //Packet dropped--> set boolean to double window size to false
                doubleWindowSize = false;
            }
            return;
        }

        //Print all results
        public void printResults() {
            for (Map.Entry<Integer, Integer> e : retransmitCount.entrySet()) {
                retransmissionTable.put(e.getValue(), retransmissionTable.getOrDefault(e.getValue(), 0) + 1);
            }
            try {
                System.out.println("Sender IP address: " + InetAddress.getLocalHost());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            System.out.println("Transmission successful!!!");
            System.out.println("Total packets sent : " + totalPacketsSent);
            System.out.println("Retransmission table: " + retransmissionTable);
            return;
        }
    }


    //Receive ACKs thread
    static class Receiver implements Runnable {

        //Method to calculate packet number from ACK
        public int calculatePacketNumber(int ack) {
            int noOfCycles = currPacketNumber / WRAPAROUND;
            if (noOfCycles * WRAPAROUND + ack > currPacketNumber) {
                return (noOfCycles - 1) * WRAPAROUND + ack;
            }
            return (noOfCycles) * WRAPAROUND + ack;
        }

        //Logic to change window size
        public void adjustWindowSize(int ackSequenceNumber) {
            if (ackSequenceNumber == lastPacketFromPreviousWindowResize && nextExpectedAck >= lastPacketFromPreviousWindowResize) {
                //Double window size in case of no failure
                if (doubleWindowSize) {
                    if (windowSize * 2 <= MAX_WINDOW_SIZE) windowSize = windowSize * 2;
                    else windowSize = MAX_WINDOW_SIZE;
                }
                //Increase window by 1 if failure already occurred
                else {
                    if (windowSize + 1 <= MAX_WINDOW_SIZE) windowSize += 1;
                    else windowSize = MAX_WINDOW_SIZE;
                }

                //Adjust window resize point
                lastPacketFromPreviousWindowResize = windowStartPointer + windowSize - 1;
                System.out.println("(C) lastWindowResizeMax reached, new windowSize: " + windowSize + "  new lastWindowResizeMax: " + lastPacketFromPreviousWindowResize);
            }
            return;
        }

        //Receiver method
        @Override
        public void run() {
            while (acksReceived.size() < totalPackets) {
                int ackSequenceNumber = 0;
                try {
                    //Read data received from server
                    int actualAck = dis.readInt();

                    //Get actual packet number
                    ackSequenceNumber = calculatePacketNumber(actualAck);

                    //Slide window if ACK for window start received
                    if (ackSequenceNumber == windowStartPointer) {
                        windowStartPointer++;
                        System.out.println("(C) Incrementing windowStartPointer to: " + windowStartPointer);
                    }

                    //Adjust window size based on received ACK seq number
                    adjustWindowSize(ackSequenceNumber);

                    //Adjust next expected ACK
                    if (ackSequenceNumber == nextExpectedAck) {
                        nextExpectedAck++;
                    }
                } catch (IOException e) {

                }
                //Save received ACK in a set
                acksReceived.add(ackSequenceNumber);
                System.out.println("AcksReceived Set : " + acksReceived.size());
            }

            //Indicate transmission over to the server
            try {
                dos.writeInt(-1);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //Print results
            System.out.println("---------------------------RESULTS---------------------------");
            System.out.println("--------------------------------------------------------------");
            System.out.println("Acknowledgements received from server: " + acksReceived.size());
            try {
                br.close();
                dis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
    }


    //Class running thread for storing data needed for logs every second
    static class DataLog implements Runnable {
        @Override
        public void run() {
            FileWriter myWriter1;
            FileWriter myWriter2;
            try {
                myWriter1 = new FileWriter("WindowSize.txt");
                myWriter2 = new FileWriter("DroppedPackets.txt");
                int i = 1;
                while (!allPacketsSent) {
                    myWriter1.write(i + " " + windowSize + "\n");
                    if (currentDroppedPacket != -1)
                        myWriter2.write(i + " " + currentDroppedPacket + "\n");
                    i++;
                    Thread.sleep(1000);
                }
                myWriter1.close();
                myWriter2.close();
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