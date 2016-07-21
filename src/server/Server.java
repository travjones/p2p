package server;

import filechunker.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Created by travis on 7/18/16.
 */
public class Server {
//    private static final int sPort = 7000;

    public static void main(String args[]) throws IOException {

        // create dir for server chunks if it does not exist
        Path path = Paths.get("server_data");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                // err creating dir
                e.printStackTrace();
            }
        }

        // first CLI argument is filename to split
        FileInfo fi = FileChunker.split(args[0]);
        System.out.println("server knows # of chunks: " + fi.numChunks);
        fi.numInitialChunks = (int) Math.ceil(fi.numChunks / 5.0);
        System.out.println("server num chunks to send: " + fi.numInitialChunks);

        String[] config = readConfig();
        final int sPort = Integer.parseInt(config[1]);
        System.out.println("Server is up on port " + sPort);
        ServerSocket ss = new ServerSocket(sPort);
        int peerNum = 1;
        try {
            while (true) {
                new ServerHandler(ss.accept(), peerNum, fi.numInitialChunks, fi.numChunks).start();
                System.out.println("Peer " + peerNum + " is connected!");
                peerNum++;
            }
        } finally {
            ss.close();
        }
    }

    private static class ServerHandler extends Thread {
        private Socket connection;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int no;
        private int numInitialChunks;
        private int numChunks;

        ServerHandler(Socket connection, int no, int numInitialChunks, int numChunks) {
            this.connection = connection;
            this.no = no;
            this.numInitialChunks = numInitialChunks;
            this.numChunks = numChunks;
        }

        public void run() {
            try {
                // init output stream
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();

                // write peerNum out to peer
                out.writeObject(no);
                out.flush();

                // write numInitialChunks out to peer
                out.writeObject(numInitialChunks);
                out.flush();

                // write numChunks out to peer
                out.writeObject(numChunks);
                out.flush();

                sendInitialChunks(connection, no, numInitialChunks);

//                FileInputStream fis;
//                BufferedInputStream bis;
//                OutputStream os;
//
//                File fileChunk = new File("./server_data/norcia2015.pdf.0");
//                byte[] byteArray  = new byte [(int)fileChunk.length()];
//                fis = new FileInputStream(fileChunk);
//                bis = new BufferedInputStream(fis);
//                bis.read(byteArray , 0, byteArray.length);
//                os = connection.getOutputStream();
//                System.out.println("Sending " + "./server_data/norcia2015.pdf.0" + "(" + byteArray.length + " bytes)");
//                os.write(byteArray, 0, byteArray.length);
//                os.flush();
//                System.out.println("Done.");

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // close out
                try {
                    out.close();
                    connection.close();
                } catch (IOException e) {
//                    e.printStackTrace();
                    System.out.println("Disconnect with Client " + no);
                }
            }

        }
    }

    private static String[] readConfig() throws IOException {
        // read in config
        BufferedReader br = new BufferedReader(new FileReader("config.txt"));
        String line = br.readLine(); // read first line
        return line.split(" ");
    }

    private static void sendInitialChunks(Socket connection, int peerNum, int numInitialChunks) throws IOException {
        String directory = "./server_data";

        File[] files = new File(directory).listFiles();

        BufferedOutputStream bos = new BufferedOutputStream(connection.getOutputStream());
        DataOutputStream dos = new DataOutputStream(bos);

//        dos.writeInt(files.length);

//        for(File file : files)
        if (peerNum < 5) {
            dos.writeInt(numInitialChunks);
            for (int i = (peerNum - 1) * numInitialChunks; i < numInitialChunks * peerNum; i++) {
                long length = files[i].length();
                dos.writeLong(length);

                String name = files[i].getName();
                dos.writeUTF(name);

                FileInputStream fis = new FileInputStream(files[i]);
                BufferedInputStream bis = new BufferedInputStream(fis);

                int b = 0;
                while ((b = bis.read()) != -1) bos.write(b);

                bis.close();
            }
        } else if (peerNum == 5) {
            dos.writeInt(files.length - (numInitialChunks * 4));
            for (int i = (peerNum - 1) * numInitialChunks; i < (files.length); i++) {
                long length = files[i].length();
                dos.writeLong(length);

                String name = files[i].getName();
                dos.writeUTF(name);

                FileInputStream fis = new FileInputStream(files[i]);
                BufferedInputStream bis = new BufferedInputStream(fis);

                int b = 0;
                while ((b = bis.read()) != -1) bos.write(b);

                bis.close();
            }
        }
        dos.close();
    }
}
