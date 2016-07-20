package server;

import filechunker.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by travis on 7/18/16.
 */
public class Server {
    public static void main(String args[]) throws IOException {
        System.out.println("Server is up!");

        // create dir for server chunks
        Path path = Paths.get("server_data");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                //fail to create directory
                e.printStackTrace();
            }
        }

        // first CLI argument filename to split
        FileChunker fc = new FileChunker();
        fc.split(args[0]);

    }
}
