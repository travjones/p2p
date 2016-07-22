package peer;

import java.io.*;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by travis on 7/18/16.
 */
public class Peer {
    private Socket requestSocket;
    private ServerSocket ss;
    private ObjectInputStream in;
    private int peerID;
    private int peerPort;
    private int peerDLID;
    private int peerULID;
    private int numInitialChunks;
    private int numChunks;
    private int peerULport;
    private int peerDLport;

    private void run() throws ClassNotFoundException, IOException {
        try {
            // create a socket to server
            requestSocket = new Socket("localhost", 7000);
            System.out.println("Connected to localhost port 7000");

            //init inputStream
            in = new ObjectInputStream(requestSocket.getInputStream());

            // set peerID from server input
            peerID = (int) in.readObject();

            // set numInitialChunks from server input
            numInitialChunks = (int) in.readObject();

            // set numChunks from server input
            numChunks = (int) in.readObject();

            // read config
            String[] config = readConfig(peerID);
            peerPort = Integer.parseInt(config[1]);
            peerDLID = Integer.parseInt(config[2]);
            peerULID = Integer.parseInt(config[3]);
            peerDLport = Integer.parseInt(config[5]);

            // create folder to hold peer chunks
            Path path = Paths.get("peer" + peerID + "_data");
            if (!Files.exists(path)) {
                try {
                    Files.createDirectories(path);
                } catch (IOException e) {
                    // err creating dir
                    e.printStackTrace();
                }
            }

            receiveInitialChunks(requestSocket, peerID);

            summaryFile(peerID);

            ServerSocket ss = new ServerSocket(peerPort);
            System.out.println("Peer is listening on " + peerPort);
            System.out.println("Waiting for peer " + (Integer.parseInt(config[2]) - 1) + " to connect...");
            new PeerDLHandler(requestSocket, peerDLport, peerID).start();
            try {
                while (true) {
                    new PeerULHandler(ss.accept(), peerID, peerPort).start();
                }
            } finally {
                ss.close();
            }
//            int bytesRead;
//            int current = 0;
//            FileOutputStream fos;
//            BufferedOutputStream bos;
//
//            // receive file
//            final int CHUNK_SIZE = 200000; // x2 just in case
//            byte [] mybytearray  = new byte [CHUNK_SIZE];
//            InputStream is = requestSocket.getInputStream();
//            fos = new FileOutputStream("./peer" + peerID + "_data/norcia2015.pdf.0");
//            bos = new BufferedOutputStream(fos);
//            bytesRead = is.read(mybytearray,0,mybytearray.length);
//            current = bytesRead;
//
//            do {
//                bytesRead =
//                        is.read(mybytearray, current, (mybytearray.length-current));
//                if(bytesRead >= 0) current += bytesRead;
//            } while(bytesRead > -1);
//
//            bos.write(mybytearray, 0 , current);
//            bos.flush();
//            System.out.println("File " + "./peer" + peerID + "_data/norcia2015.pdf.0"
//                    + " downloaded (" + current + " bytes read)");

//            while (true) {}

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close connections
            in.close();
            requestSocket.close();
        }

    }

    private static class PeerULHandler extends Thread {
        private Socket connection;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int peerID;
        private int pPort;

        PeerULHandler(Socket connection, int peerID, int pPort) {
            this.connection = connection;
            this.peerID = peerID;
            this.pPort = pPort;
        }

        public void run() {
            System.out.println("Peer connected on " + pPort + "...");
            try {
                ArrayList<String> chunksToSend;
                chunksToSend = sendChunkList(connection, peerID);
                sendChunks(chunksToSend, peerID, connection);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private static class PeerDLHandler extends Thread {
        private Socket requestSocket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int pDLPort;
        private int peerID;

        PeerDLHandler(Socket requestSocket, int pDLPort, int peerID) {
            this.requestSocket = requestSocket;
            this.pDLPort = pDLPort;
            this.peerID = peerID;
        }

        public void run() {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        requestSocket = new Socket("localhost", pDLPort);
                        System.out.println("Connected to UL peer on " + pDLPort);
                        timer.cancel();
                        timer.purge();

                        ArrayList<String> chunks;
                        chunks = (ArrayList<String>) receiveChunkList(requestSocket, peerID);
                        receiveChunks(peerID, requestSocket);
                    } catch (IOException e) {
                        System.out.println("Could not connect to UL peer...");
//                        e.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 1000);
        }
    }

    private static String[] readConfig(int peerID) throws IOException {
        // read in config
        BufferedReader br = new BufferedReader(new FileReader("config.txt"));
        for (int i = 0; i < peerID; i++) {
            br.readLine();
        }
        String line = br.readLine();
        return line.split(" ");
    }

    private static void receiveInitialChunks(Socket requestSocket, int peerID) throws IOException {
        String dirPath = "./peer" + peerID + "_data";

        BufferedInputStream bis = new BufferedInputStream(requestSocket.getInputStream());
        DataInputStream dis = new DataInputStream(bis);

        int filesCount = dis.readInt();
        File[] files = new File[filesCount];

        for (int i = 0; i < filesCount; i++) {
            long fileLength = dis.readLong();
            String fileName = dis.readUTF();

            files[i] = new File(dirPath + "/" + fileName);

            FileOutputStream fos = new FileOutputStream(files[i]);
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            for (int j = 0; j < fileLength; j++) bos.write(bis.read());

            bos.close();
        }

        dis.close();
    }

    private static void summaryFile(int peerID) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter("peer" + peerID + "summary.txt");
        File[] files = new File("./peer" + peerID + "_data").listFiles();
        System.out.println(files.length);
        for (File file : files) {
            System.out.println(file.getName());
            pw.println(file.getName());
        }
        pw.close();
    }

    public static ArrayList<String> sendChunkList(Socket connection, int peerID) throws IOException, ClassNotFoundException {
        String filename = "./peer" + peerID + "summary.txt";
        BufferedReader br = new BufferedReader(new FileReader(filename));

        List<String> chunkList = new ArrayList<>();
        String line = null;
        while ((line = br.readLine()) != null) {
            chunkList.add(line);
//            String[] shards = line.split("\\.");
//            chunkList.add(shards[2]);
        }
        br.close();


        BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.flush();
        oos.writeObject(chunkList);
        oos.flush();

        BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
        ObjectInputStream ois = new ObjectInputStream(bis);
        ArrayList<String> chunksToSend;
        chunksToSend = (ArrayList<String>) ois.readObject();
        return chunksToSend;
    }

    public static List<String> receiveChunkList(Socket requestSocket, int peerID) throws IOException, ClassNotFoundException {
        ArrayList<String> chunkList;
        BufferedInputStream bis = new BufferedInputStream(requestSocket.getInputStream());
        ObjectInputStream ois = new ObjectInputStream(bis);
        chunkList = (ArrayList<String>) ois.readObject();
        System.out.println("Received chunk list: " + Arrays.toString(chunkList.toArray()));

        String filename = "./peer" + peerID + "summary.txt";
        BufferedReader br = new BufferedReader(new FileReader(filename));

        List<String> chunksToGetList = new ArrayList<>();
        List<String> myChunkList = new ArrayList<>();
        String line = null;
        while ((line = br.readLine()) != null) {
            myChunkList.add(line);
//            String[] shards = line.split("\\.");
//            myChunkList.add(shards[2]);
        }
        br.close();

        for (String el : chunkList) {
            if (!myChunkList.contains(el)) {
                chunksToGetList.add(el);
            }
        }
        System.out.println("My chunk list: " + Arrays.toString(myChunkList.toArray()));
        System.out.println("Chunks to request: " + Arrays.toString(chunksToGetList.toArray()));

        BufferedOutputStream bos = new BufferedOutputStream(requestSocket.getOutputStream());
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.flush();
        oos.writeObject(chunksToGetList);
        oos.flush();

        return chunksToGetList;

//        chunksToGetList.addAll(chunkList.stream().filter(myChunkList::contains).collect(Collectors.toList()));
    }

    public static void sendChunks(ArrayList<String> reqChunks, int peerID, Socket connection) throws IOException {
        String dir = "./peer" + peerID + "_data";

        BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());
        DataOutputStream dos = new DataOutputStream(bos);

        dos.writeInt(reqChunks.size());
        for (String chunk : reqChunks) {
            File fileChunk = new File(dir + "/" + chunk);
            long length = fileChunk.length();
            dos.writeLong(length);

            String name = fileChunk.getName();
            dos.writeUTF(name);

            FileInputStream fis = new FileInputStream(fileChunk);
            BufferedInputStream bis = new BufferedInputStream(fis);

            int b = 0;
            while ((b = bis.read()) != -1) bos.write(b);

            bis.close();
        }
        dos.close();
    }

    public static void receiveChunks(int peerID, Socket connection) throws IOException {
        String dir = "./peer" + peerID + "_data";
        BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
        DataInputStream dis = new DataInputStream(bis);

        int filesCount = dis.readInt();
        File[] files = new File[filesCount];

        for (int i = 0; i < filesCount; i++) {
            long fileLength = dis.readLong();
            String fileName = dis.readUTF();

            files[i] = new File(dir + "/" + fileName);

            FileOutputStream fos = new FileOutputStream(files[i]);
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            for (int j = 0; j < fileLength; j++) bos.write(bis.read());

            bos.close();
        }

        dis.close();

        // update summary file
        summaryFile(peerID);

    }

    public static void main(String[] args) throws ClassNotFoundException, IOException {
        Peer peer = new Peer();
        peer.run();
    }
}
