package filechunker;

import java.io.*;

/**
 * Created by travis on 7/20/16.
 */
public class FileChunker {

    // chunk size 100KB
    public static long chunkSize = 100000;

    public static FileInfo split(String filename) throws IOException {
        // open file
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename));

        // determine length of file
        File f = new File(filename);
        long fileSize = f.length();

        int chunkID;
        for (chunkID = 0; chunkID < fileSize / chunkSize; chunkID++) {
            // open output file
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream("./server_data/" + filename + "." + chunkID));

            // write bytes to file
            for (int currentByte = 0; currentByte < chunkSize; currentByte++) {
                out.write(in.read());
            }

            out.close();
        }

        // last chunk probably smaller than chunk size
        if (fileSize != chunkSize * (chunkID - 1)) {
            // open output file
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream("./server_data/" + filename + "." + chunkID));

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }

            // close output file
            out.close();

            chunkID++;
        }

        // close input file
        in.close();

        FileInfo fi = new FileInfo();
        fi.filename = filename;
        fi.fileSize = fileSize;
        fi.numChunks = chunkID;

        return fi;
    }

    public static void join(String baseFilename, String dlDir, String chunkDir) throws IOException {
        int numParts = getNumParts(chunkDir + baseFilename);

        // reassemble parts
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dlDir + baseFilename));
        for (int part = 0; part < numParts; part++) {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(chunkDir + baseFilename + "." + part));

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            // close part
            in.close();
        }
        out.close();
    }

    private static int getNumParts(String baseFilename) throws IOException {
        // ls files in directory
        File dir = new File(baseFilename).getAbsoluteFile().getParentFile();

        // create new file with baseFilename as the name
        final String fileName = new File(baseFilename).getName();

        // find file chunks
        String[] files = dir.list(new FilenameFilter() {
            public boolean accept(File file, String s) {
                return s.startsWith(fileName) && s.substring(fileName.length()).matches("^\\.\\d+$");
            }
        });

        return files.length;
    }

}

