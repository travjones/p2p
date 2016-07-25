package peer;

import filechunker.FileChunker;

import java.io.*;
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
    private int numInitialChunks;
    private int numChunks;
    private int peerULport;
    private int peerDLport;

    private void run() throws ClassNotFoundException, IOException, InterruptedException {
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
            peerULport = Integer.parseInt(config[4]);
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

            // receive initial chunks from server
            receiveInitialChunks(requestSocket, peerID);

            // update summary file
            summaryFile(peerID);

            // new server socket
            ServerSocket ss = new ServerSocket(peerPort);
            System.out.println("Peer is listening on " + peerPort);
            System.out.println("Waiting for peer " + (Integer.parseInt(config[2]) - 1) + " to connect...");

            // instantiate, start, and join DL and UL handlers
            PeerDLHandler pdlh = new PeerDLHandler(requestSocket, peerDLport, peerID, numChunks);
            pdlh.start();
            pdlh.join();
            PeerULHandler pulh = new PeerULHandler(ss.accept(), peerID, peerPort, numChunks);
            pulh.start();
            pulh.join();

            // close server socket
            ss.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Close connections
            in.close();
            requestSocket.close();

            // rejoin chunks
            reassembleChunks(peerID);
        }

    }

    private static class PeerULHandler extends Thread {
        private Socket connection;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int peerID;
        private int pPort;
        private int numChunks;

        PeerULHandler(Socket connection, int peerID, int pPort, int numChunks) {
            this.connection = connection;
            this.peerID = peerID;
            this.pPort = pPort;
            this.numChunks = numChunks;
        }

        public void run() {
            System.out.println("Peer connected on " + pPort + "...");
            try {
                ArrayList<String> chunksToSend;
                ArrayList<String> chunks;

                do {
                    chunksToSend = sendChunkList(connection, peerID);
                    sendChunks(chunksToSend, peerID, connection);

                    chunks = (ArrayList<String>) receiveChunkList(connection, peerID);
                    receiveChunks(peerID, connection);

                    int numChunksPeer = peerChunkCount(peerID);
                    if (numChunksPeer == numChunks && chunksToSend.isEmpty() && chunks.isEmpty()) break;

                } while (true);
                Thread.currentThread().interrupt();

            } catch (IOException | ClassNotFoundException e) {
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
        private int numChunks;

        PeerDLHandler(Socket requestSocket, int pDLPort, int peerID, int numChunks) {
            this.requestSocket = requestSocket;
            this.pDLPort = pDLPort;
            this.peerID = peerID;
            this.numChunks = numChunks;
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
                        ArrayList<String> chunksToSend;
                        do {
                            chunks = (ArrayList<String>) receiveChunkList(requestSocket, peerID);
                            receiveChunks(peerID, requestSocket);

                            chunksToSend = sendChunkList(requestSocket, peerID);
                            sendChunks(chunksToSend, peerID, requestSocket);

                            int numChunksPeer = peerChunkCount(peerID);
                            if (numChunksPeer == numChunks && chunks.isEmpty() && chunksToSend.isEmpty()) break;
                        } while (true);

                        Thread.currentThread().interrupt();
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
            System.out.println("Received initial chunk from server " + fileName);
        }

        dis.close();
    }

    private static void summaryFile(int peerID) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter("peer" + peerID + "summary.txt");
        File[] files = new File("./peer" + peerID + "_data").listFiles();
        for (File file : files) {
            pw.println(file.getName());
        }
        pw.close();
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<String> sendChunkList(Socket connection, int peerID) throws IOException, ClassNotFoundException {
        String filename = "./peer" + peerID + "summary.txt";
        BufferedReader br = new BufferedReader(new FileReader(filename));

        List<String> chunkList = new ArrayList<>();
        String line = null;
        while ((line = br.readLine()) != null) {
            chunkList.add(line);
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
        if (!chunksToSend.isEmpty()) {
            System.out.println("CHUNKS TO SEND: " + Arrays.toString(chunksToSend.toArray()));
        }
        return chunksToSend;
    }

    @SuppressWarnings("unchecked")
    private static List<String> receiveChunkList(Socket requestSocket, int peerID) throws IOException, ClassNotFoundException {
        ArrayList<String> chunkList;
        BufferedInputStream bis = new BufferedInputStream(requestSocket.getInputStream());
        ObjectInputStream ois = new ObjectInputStream(bis);
        chunkList = (ArrayList<String>) ois.readObject();

        String filename = "./peer" + peerID + "summary.txt";
        BufferedReader br = new BufferedReader(new FileReader(filename));

        List<String> chunksToGetList = new ArrayList<>();
        List<String> myChunkList = new ArrayList<>();
        String line = null;
        while ((line = br.readLine()) != null) {
            myChunkList.add(line);
        }
        br.close();

        for (String el : chunkList) {
            if (!myChunkList.contains(el)) {
                chunksToGetList.add(el);
            }
        }

        BufferedOutputStream bos = new BufferedOutputStream(requestSocket.getOutputStream());
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.flush();
        oos.writeObject(chunksToGetList);
        oos.flush();

        if (!chunksToGetList.isEmpty()) {
            System.out.println("CHUNKS TO REQUEST: " + Arrays.toString(chunksToGetList.toArray()));
        }

        return chunksToGetList;

    }


    private static void sendChunks(ArrayList<String> reqChunks, int peerID, Socket connection) throws IOException {
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

//            bis.close();
            System.out.println("SENDING CHUNK: " + name);
        }
//        dos.close();
        dos.flush();
    }

    private static void receiveChunks(int peerID, Socket connection) throws IOException {
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
            fos.flush();
            System.out.println("RECEIVING CHUNK: " + fileName);

        }
//        dis.close();

        // update summary file
        summaryFile(peerID);

    }

    private static synchronized void reassembleChunks(int peerID) throws IOException {
        // create dir for server chunks if it does not exist
        Path path = Paths.get("./peer" + peerID + "_download");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                // err creating dir
                e.printStackTrace();
            }
        }

        System.out.println("RECOMBINING CHUNKS...");
        //maybe put this in a function
        String chunkDir = "./peer" + peerID + "_data/";

        String dlDir = "./peer" + peerID + "_download/";

        File[] files = new File(chunkDir).listFiles();

        assert files != null;
        String[] shards = files[0].getName().split("\\.");

        String filename = shards[0] + "." + shards[1];

        FileChunker.join(filename, dlDir, chunkDir);

        System.out.println("FILE TRANSFERRED SUCCESSFULLY!");
    }

    private static int peerChunkCount(int peerID) {
        String directory = "./peer" + peerID + "_data";

        File[] files = new File(directory).listFiles();

        assert files != null;

        return files.length;
    }

    public static void main(String[] args) throws ClassNotFoundException, IOException, InterruptedException {
        Peer peer = new Peer();
        peer.run();
    }
}
