package server;

import filechunker.*;

import java.io.IOException;

/**
 * Created by travis on 7/18/16.
 */
public class Server {
    public static void main(String args[]) throws IOException {
        FileChunker fc = new FileChunker();
        fc.split(args[0]);
    }
}
