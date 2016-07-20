package filechunker;

import java.io.*;

/**
 * Created by travis on 7/20/16.
 */
public class FileChunker {

    // chunk size 100KB
    public static long chunkSize = 100000;

//    public static void main(String[] args) throws Exception
//    {
//        if (args.length != 2)
//        {
//            System.out.println("Must specify a flag -split or -join and a file argument. The file argument for splitting is the file to split and for joining is the base filename to join on.");
//            System.exit(0);
//        }
//
//        try
//        {
//            if (args[0].equalsIgnoreCase("-split"))
//                split(args[1]);
//            else if (args[0].equalsIgnoreCase("-join"))
//                join(args[1]);
//            else
//            {
//                System.out.println("The first argument must be an option:");
//                System.out.println("\t-split: split the specified file");
//                System.out.println("\t-join: join all splitter outfiles with the specified base filename");
//                System.exit(0);
//            }
//        }
//        catch (FileNotFoundException e)
//        {
//            System.out.println("File not found: " + args[1]);
//        }
//        catch (IOException e)
//        {
//            System.out.println("IO Error");
//        }
//    }


    public static void split(String filename) throws IOException {
        // open file
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename));

        // determine length of file
        File f = new File(filename);
        long fileSize = f.length();

        int chunkID;
        for (chunkID = 0; chunkID < fileSize / chunkSize; chunkID++) {
            // open output file
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filename + "." + chunkID));

            // write bytes to file
            for (int currentByte = 0; currentByte < chunkSize; currentByte++) {
                out.write(in.read());
            }

            out.close();
        }

        // last chunk might be smaller than chunk size
        if (fileSize != chunkSize * (chunkID - 1)) {
            // open output file
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filename + "." + chunkID));

            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }

            // close output file
            out.close();
        }

        // close input file
        in.close();
    }

    public static void join(String baseFilename) throws IOException {
        int numParts = getNumParts(baseFilename);
        System.out.println(numParts);

        // reassemble parts
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(baseFilename));
        for (int part = 0; part < numParts; part++) {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(baseFilename + "." + part));

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
