package peer;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Created by travis on 7/18/16.
 */
public class Peer {
    Socket requestSocket;
    ObjectInputStream in;
    int peerID;
    int peerPort;
    int peerDLID;
    int peerULID;
    int numInitialChunks;
    int numChunks;

    void run() throws ClassNotFoundException, IOException {
        try {
            // create a socket to server
            Socket requestSocket = new Socket("localhost", 7000);
            System.out.println("Connected to localhost port 7000");

            //init inputStream
            in = new ObjectInputStream(requestSocket.getInputStream());

            // set peerID from server input
            peerID = (int) in.readObject();
            System.out.println(peerID);

            // set numInitialChunks from server input
            numInitialChunks = (int) in.readObject();
            System.out.println(numInitialChunks);

            // set numChunks from server input
            numChunks = (int) in.readObject();
            System.out.println(numChunks);

            // read config
            String[] config = readConfig(peerID);
            System.out.println(Arrays.toString(config));
            peerPort = Integer.parseInt(config[1]);
            peerDLID = Integer.parseInt(config[2]);
            peerULID = Integer.parseInt(config[3]);

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

            while (true) {
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close connections
            in.close();
            requestSocket.close();
        }

    }

    public static String[] readConfig(int peerID) throws IOException {
        // read in config
        BufferedReader br = new BufferedReader(new FileReader("config.txt"));
        for (int i = 0; i < peerID; i++) {
            br.readLine();
        }
        String line = br.readLine();
        String[] contents = line.split(" "); // split on space
        return contents;
    }

    public static void receiveInitialChunks(Socket requestSocket, int peerID) throws IOException {
        String dirPath = "./peer" + peerID + "_data";

        BufferedInputStream bis = new BufferedInputStream(requestSocket.getInputStream());
        DataInputStream dis = new DataInputStream(bis);

        int filesCount = dis.readInt();
        File[] files = new File[filesCount];

        for(int i = 0; i < filesCount; i++)
        {
            long fileLength = dis.readLong();
            String fileName = dis.readUTF();

            files[i] = new File(dirPath + "/" + fileName);

            FileOutputStream fos = new FileOutputStream(files[i]);
            BufferedOutputStream bos = new BufferedOutputStream(fos);

            for(int j = 0; j < fileLength; j++) bos.write(bis.read());

            bos.close();
        }

        dis.close();
    }

    public static void main(String[] args) throws ClassNotFoundException, IOException {
        Peer peer = new Peer();
        peer.run();
    }
}
