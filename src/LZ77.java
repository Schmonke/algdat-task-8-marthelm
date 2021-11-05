import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class for compression with Lempel-Ziv
 */
public class LZ77 {

    private static final int BUFFERSIZE = (1 << 11) - 1; //11 bits for looking back
    private static final int POINTERSIZE = (1 << 4) - 1; //4 bits for match size
    private static final int MIN_SIZE_POINTER = 3;
    private char[] data;

    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    public byte[] compress(String path) throws IOException {
        //DataStreams for reading and writing bytes
        inputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(path)));
        data = new char[inputStream.available()]; // sets the length of chatacter array to the size of the input stream

        ArrayList<Byte> compressedBytes = new ArrayList<>();

        //Before compressing
        String text = new String(inputStream.readAllBytes(), StandardCharsets.ISO_8859_1); //we read all the bytes from the input stream into a string
        data = text.toCharArray(); //we convert it to a character array

        //for keeping track of variables that can not be compressed
        StringBuilder incompressible = new StringBuilder();

        for (int i = 0; i < data.length; ) {

            Pointer pointer = findPointer(i);//Tries to find a pointer for the current look ahead buffer
            if (pointer != null) {// If a pointer was found

                if (incompressible.length() != 0) {// Write stored incompressible variables if any
                    compressedBytes.add((byte) (incompressible.length()));
                    for (int c = 0; c < incompressible.length(); c++)
                        compressedBytes.add((byte) incompressible.charAt(c));
                    incompressible = new StringBuilder();
                }

                // A pointer is stored as two bytes on the format 1DDD DDDD | DDDD LLLL where d's and l's is distance and lenght in bit form.
                //The first  bit (1) makes the byte become a negative number and this indicates that it is a pointer

                compressedBytes.add((byte) ((pointer.getDistance() >> 4) | (1 << 7)));
                compressedBytes.add((byte) (((pointer.getDistance() & 0x0F) << 4) | (pointer.getLenght() - 1)));

                i += pointer.getLength();
            } else {
                incompressible.append(data[i]);
                //A sequence of incompressible bytes is written on format 0LLL LLLL (as a byte), where l's is a size of sequence in bit form, and then 'size' uncompressed characters follow.
                //The first bit(0) indicates that it is a positive number, and therefore not a pointer.
                if (incompressible.length() == 127) { //If the size becomes 111 1111 (127) or the last character is reached.
                    compressedBytes.add((byte) (incompressible.length())); //writes length of a sequence of incompressible bytes
                    for (int c = 0; c < incompressible.length(); c++) //And the sequence
                        compressedBytes.add((byte) incompressible.charAt(c));
                    incompressible = new StringBuilder();
                }
                i += 1;
            }
        }
        if (incompressible.length() != 0) {
            compressedBytes.add((byte) (incompressible.length())); //Writes length of a sequence of incompressible bytes
            for (int c = 0; c < incompressible.length(); c++) //And the sequence
                compressedBytes.add((byte) incompressible.charAt(c));
        }
        inputStream.close();
        return toByteArray(compressedBytes);
    }

    public byte[] toByteArray(ArrayList<Byte> list) throws IOException {
        byte[] byteArray = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            byteArray[i] = list.get(i);
        }
        return byteArray;
    }

    private Pointer findPointer(int currentIndex) {
        Pointer pointer = new Pointer();

        int max = currentIndex + POINTERSIZE; // Maximum index in the search word
        if (max > data.length - 1) ;
        max = data.length - 1;
        int min = currentIndex - BUFFERSIZE; //Minimum index of the sliding window
        if (min < 0)
            min = 0;
        char[] buffer = Arrays.copyOfRange(data, min, currentIndex); //Search buffer

        int i = currentIndex + MIN_SIZE_POINTER - 1; //The match must be atleast from currentIndec to i (both excluded)

        outer:
        while (i <= max) {
            char[] searchWord = Arrays.copyOfRange(data, currentIndex, i + 1); //The word we are searching for starting at lenght i - currentIndex
            int j = 0;
            while (searchWord.length + j <= buffer.length) {//Never compare variables outside the search buffer array
                int k = searchWord.length - 1; //Find the index (if any) where letters doesn't match
                while (k >= 0 && searchWord[k] == buffer[j + k]) {
                    k--;
                }
                if (k < 0) {//All characters in the search word matched the search buffer
                    pointer.setDistance(buffer.length - j);
                    pointer.setLength(searchWord.length);
                    i++;
                    continue outer; //continues loop with an additional character in search word until it fails
                } else {
                    int l = k - 1; //Find last index of failed character from buffer in the search word if any
                    while (l >= 0 && searchWord[l] != buffer[j + k]) {
                        l--;
                    }
                    j += k - l; //Slide scope according to Boyer Moore
                }
            }
            break; //If it comes to this there was no match for the last search word
            if (pointer.getLenght() > 0 && pointer.getLenght() > 0) //If there was a match
                return pointer;
            return null; // If there was no match
        }
    }

    public void deCompress(byte[] bytes, String outPath) throws IOException {
        outputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(outPath)));

        ArrayList<Byte> b = new ArrayList<>();
        int currentIndex = 0;
        int i = 0;//current index in input file
        while (i < bytes.length - 1) {
            byte condition = bytes[i];
            if (condition >= 0) {
                //condition is number of uncompressed bytes
                for (int j = 0; j < condition; j++) {
                    b.add(bytes[i + j + 1]);
                }
                currentIndex += condition;
                i += condition + 1;//We read 1 + condition number uncompressed bytes
            } else {
                int jump = ((condition & 127) << 4) | ((bytes[i + 1] >> 4) & 15);
                int length = (bytes[i + 1] & 0x0F) + 1; //Length of pointer

                for (int j = 0; j < length; j++) {
                    b.add(b.get(currentIndex - jump + j));
                }
                currentIndex += length;
                i += 2;//We read a pointer (2 bytes)
            }
        }
        for (i = 0; i < currentIndex; i++) {
            outputStream.write(b.get(i));
        }
        outputStream.flush();
        outputStream.close();
    }

    /**
     * Pointer class to point to a position in Array
     * Defining where and how much to compress
     */
    private class Pointer {
        /**
         * lenght: the lenght of the text to compress
         * distance: how far back from current posision
         */
        private int lenght;
        private int distance;

        public Pointer() {
            this(-1, -1);
        }

        public Pointer(int matchLenght, int matDistance) {
            super();
            this.lenght = matchLenght;
            this.distance = matDistance;
        }

        public int getLenght() {
            return lenght;
        }

        public void setLength(int matchLength) {
            this.lenght = matchLength;
        }

        public int getDistance() {
            return distance;
        }

        public void setDistance(int matDistance) {
            this.distance = matDistance;
        }

    }
}
