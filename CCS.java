import java.io.*;
import java.net.*;
import java.util.*;

public class CCS {

    //stats objects
    private static final Stats stats = new Stats();
    private static final Stats last10SecStats = new Stats();
    private static int port;

    //size of a buffer
    private static final int arraySize = 12;

    public static void main(String... args) throws Exception {

        if (args.length != 1) {
            throw new Exception("Wrong number of arguments, try again: java -jar CCS.jar <port>");
        }

        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong argument");
        }

        Statistics();
        System.out.println("starting to count the statistics");

        try{
            DatagramSocket socketUDP = new DatagramSocket(port);
            ServerSocket socketTCP = new ServerSocket(port);
            System.out.println("starting to listen on the port: "+port);
            Thread udpThread = new Thread(() -> UDP(socketUDP));
            udpThread.start();
            Thread tcpThread = new Thread(() -> TCP(socketTCP));
            tcpThread.start();
        } catch (SocketException e) {
            System.err.println("Error opening port: "+port);
            e.printStackTrace();
        }
    }
    public static void UDP(DatagramSocket socket){
        byte[] buffer = new byte[arraySize];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                System.err.println("Error receiving packet!");
            }
            String received =  new String(packet.getData(), 0, packet.getLength());
            if(received.startsWith("CCS DISCOVER")){
                try{
                    byte[] ret = "CCS FOUND".getBytes();
                    DatagramPacket retPacket = new DatagramPacket(ret,ret.length,packet.getAddress(),packet.getPort());
                    socket.send(retPacket);
                    System.out.println("Return packet sent");
                }catch (IOException e){
                    System.err.println("Error sending return packet!");
                }
            }

        }
    }
    public static void TCP(ServerSocket socket){
        try {
            while (true) {
                Socket clientSocket = socket.accept();
                ClientHandler(clientSocket);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    public static void Statistics(){
        Thread t = new Thread(()->{
            while (true){
                try {
                    Thread.sleep(10000);
                    last10SecStats.transfer(stats);
                    System.out.println("Statystiki od rozpoczęcia: \n"+stats);
                    System.out.println("Statystiki z ostatnich 10 sekund: \n"+last10SecStats);
                    last10SecStats.reset();
                } catch (InterruptedException e) {
                    System.out.println("Timer przerwany.");
                    break;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }
    //tcp single client handler
    private static void ClientHandler(Socket socket){
        Thread t = new Thread(() -> {
            try {
                last10SecStats.newClientUpdate();
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while(true){
                    String input = in.readLine();
                    List<String> listOfArgs = Arrays.asList(input.split(" "));
                    if (listOfArgs.size()!=3){
                        last10SecStats.invalidOperation();
                        out.write("ERROR\n");
                        out.flush();
                        System.out.println("ERROR - Zła ilość argumentów");
                        continue;
                    }
                    int result = 0;
                    try{
                        String oper = listOfArgs.get(0);
                        int arg1 = Integer.parseInt(listOfArgs.get(1));
                        int arg2 = Integer.parseInt(listOfArgs.get(2));

                        switch (oper){
                            case "ADD":
                                result = arg1+arg2;
                                break;
                            case "SUB":
                                result = arg1-arg2;
                                break;
                            case "MUL":
                                result = arg1*arg2;
                                break;
                            case "DIV":
                                if (arg2 == 0){
                                    last10SecStats.invalidOperation();
                                    out.write("ERROR\n");
                                    out.flush();
                                    System.out.println("ERROR - Dzielenie przez 0");
                                    continue;
                                }else{
                                    result = arg1/arg2;
                                }
                                break;
                            default:
                                last10SecStats.invalidOperation();
                                out.write("ERROR\n");
                                out.flush();
                                System.out.println("ERROR - Zły argument operacji");
                                continue;
                        }
                        last10SecStats.updateAfterStats(oper,result);
                        out.write(result+"\n");
                        out.flush();
                        System.out.println(oper+" "+result);
                    }catch (Exception e){
                        out.write("ERROR\n");
                        out.flush();
                        System.out.println("ERROR - Zły typ argumentów");
                    }
                }
            } catch (IOException e){
                System.out.println("Socket został zamknięty");
            }
        });
        t.start();
    }
}
class Stats {
    private int connectedClients;
    private int computedOperations;

    private int invalidOperations;
    private final Map<String, Integer> operationsNumber;

    private int sumOfResults;

    public Stats() {
        this.connectedClients = 0;
        this.computedOperations = 0;
        this.invalidOperations = 0;
        this.operationsNumber = new HashMap<>();
        operationsNumber.put("ADD", 0);
        operationsNumber.put("SUB", 0);
        operationsNumber.put("MUL", 0);
        operationsNumber.put("DIV", 0);
        this.sumOfResults = 0;
    }
    public synchronized void updateAfterStats(String op,Integer result){
        operationsNumber.merge(op,1,Integer::sum);
        computedOperations++;
        sumOfResults+=result;
    }
    public synchronized void newClientUpdate(){
        connectedClients++;
    }
    public synchronized void invalidOperation(){
        invalidOperations++;
    }
    //transfer stats to another stats object
    public synchronized void transfer(Stats transferTo){
        transferTo.computedOperations += computedOperations;
        transferTo.invalidOperations += invalidOperations;
        transferTo.connectedClients += connectedClients;
        transferTo.sumOfResults += sumOfResults;
        operationsNumber.forEach((k,v) -> {
            transferTo.operationsNumber.merge(k,v,Integer::sum);
        });
    }
    public synchronized void reset() {
        connectedClients = 0;
        computedOperations = 0;
        invalidOperations = 0;
        sumOfResults = 0;
        operationsNumber.replaceAll((k, v) -> 0);
    }

    @Override
    public synchronized String toString() {
        return "==================================\n"+
                "Nowo podlaczeni klienci: " + connectedClients +
                "\nObliczone operacje: "  + computedOperations +
                "\nBledne operacje: " + invalidOperations +
                "\nPoszczegolne operacje: " + operationsNumber +
                "\nSuma wynikow: " + sumOfResults+
                "\n==================================";
    }
}
