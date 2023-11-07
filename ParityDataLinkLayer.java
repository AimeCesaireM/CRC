// =============================================================================
// IMPORTS

import java.util.*;
// =============================================================================


// =============================================================================
/**
 * @file   ParityDataLinkLayer.java
 * @author Aime Cesaire Mugishawayo (cmugishawayo25@amherst.edu)
 * @date   September 2023,


 * A data link layer that uses start/stop tags and byte packing to frame the
 * data, and that performs no error management.
 */
public class ParityDataLinkLayer extends DataLinkLayer {
// =============================================================================



    @Override
    public void send (byte[] data) {

        int counter = 0;

        //while you have data to send
        while (counter < data.length) {
            // we will not send more than 8
            int numberOfBytes = Math.min(8, data.length - counter);

            byte[] dataToFrame = new byte[numberOfBytes];
            // grab the 8 or fewer bytes of data
            for (int i = 0; i < numberOfBytes; ++i){
                dataToFrame[i] = data[counter];
                counter ++;

            }
            //frame the 8 or fewer bytes of data
            byte[] framedData = createFrame(dataToFrame);
//            System.out.println("[=] Frame sent" + new String(framedData));

            // send each frame on its way.
            for (byte frameByte : framedData) {
                transmit(frameByte);
            }
        }

    }
    protected byte[] createFrame (byte[] data) {
        // this has barely been changed except for the parity code.
        System.out.println("[=] Data to Frame: " + new String(data));
        byte[] dataWithParity = new byte[data.length + 1];

        System.arraycopy(data, 0, dataWithParity, 0, data.length);
        dataWithParity[data.length] = checkParity(data);

        Queue<Byte> framingData = new LinkedList<>();

        // Begin with the start tag.
        framingData.add(startTag);

        // Add each byte of original data.
        for (int i = 0; i < dataWithParity.length; i += 1) {

            // If the current data byte is itself a metadata tag, then precede
            // it with an escape tag.
            byte currentByte = dataWithParity[i];
            if ((currentByte == startTag) ||
                    (currentByte == stopTag) ||
                    (currentByte == escapeTag)) {

                framingData.add(escapeTag);

            }

            // Add the data byte itself
            framingData.add(currentByte);

        }

        // End with a stop tag.
        framingData.add(stopTag);

        // Convert to the desired byte array.
        byte[] framedData = new byte[framingData.size()];
        Iterator<Byte>  i = framingData.iterator();
        int             j = 0;
        while (i.hasNext()) {
            framedData[j++] = i.next();
        }

        return framedData;

    }

    protected byte[] processFrame () {

        // Search for a start tag.  Discard anything prior to it.
        boolean        startTagFound = false;
        Iterator<Byte>             i = byteBuffer.iterator();
        while (!startTagFound && i.hasNext()) {
            byte current = i.next();
            if (current != startTag) {
                i.remove();
            } else {
                startTagFound = true;
            }
        }

        // If there is no start tag, then there is no frame.
        if (!startTagFound) {
            return null;
        }

        // Try to extract data while waiting for an unescaped stop tag.
        Queue<Byte> extractedBytes = new LinkedList<>();
        boolean       stopTagFound = false;
        while (!stopTagFound && i.hasNext()) {

            // Grab the next byte.  If it is...
            //   (a) An escape tag: Skip over it and grab what follows as
            //                      literal data.
            //   (b) A stop tag:    Remove all processed bytes from the buffer and
            //                      end extraction.
            //   (c) A start tag:   All that precedes is damaged, so remove it
            //                      from the buffer and restart extraction.
            //   (d) Otherwise:     Take it as literal data.
            byte current = i.next();
            if (current == escapeTag) {
                if (i.hasNext()) {
                    current = i.next();
                    extractedBytes.add(current);
                } else {
                    // An escape was the last byte available, so this is not a
                    // complete frame.
                    return null;
                }
            } else if (current == stopTag) {
                cleanBufferUpTo(i);
                stopTagFound = true;
            } else if (current == startTag) {
                cleanBufferUpTo(i);
                extractedBytes = new LinkedList<>();
            } else {
                extractedBytes.add(current);
            }

        }

        // If there is no stop tag, then the frame is incomplete.
        if (!stopTagFound) {
            return null;
        }

        // Convert to the desired byte array.
        if (debug) {
            System.out.println("DumbDataLinkLayer.processFrame(): Got whole frame!");
        }


        byte[] extractedData = new byte[extractedBytes.size()];
        int                j = 0;
        i = extractedBytes.iterator();
        while (i.hasNext()) {
            extractedData[j] = i.next();
            if (debug) {
                System.out.printf("DumbDataLinkLayer.processFrame():\tbyte[%d] = %c\n",
                        j,
                        extractedData[j]);
            }
            j += 1;
        }

//        Since we are using even parity, the parity of every frame that has been previously 'treated' should always be 0
        byte parityByte = checkParity(extractedData);
        // strip the parity byte off the data we send to host.
        if (extractedData.length <= 1){
            System.out.println("Start/Stop tag was corrupted");
            System.out.println("Corrupted data: " + new String(extractedData));
            return null;
        }
        byte[] dataMinusParity = new byte[extractedData.length - 1];
        System.arraycopy(extractedData, 0, dataMinusParity, 0, dataMinusParity.length);

        if (parityByte == 0) {
            return dataMinusParity;
        }
        // handle the corrupted data as asked in the assignment.; should look gargled when printed
        else{
            System.err.println("[-] Parity Check :" + parityByte + " should be 0; indicates corrupted data.");
            System.err.println("[-]Thrown out frame: " + new String(dataMinusParity) + " \n");
            return null;
        }

    } // processFrame ()

    private void cleanBufferUpTo (Iterator<Byte> end) {

        Iterator<Byte> i = byteBuffer.iterator();
        while (i.hasNext() && i != end) {
            i.next();
            i.remove();
        }

    }


    // method to check even parity of a  byte
    private byte checkParity(byte b) {
        int count = 0;
        while (b != 0) {
//            increment if last bit is 1, do nothing otherwise
            count += b & 1;
//           zero fill right shift byte
            b >>>= 1;
        }
        //0 means even number of 1s
        return (byte) (count % 2);
    }


    //To get frame's parity, repeatedly call parity of bytes and combine parities.
    private byte checkParity(byte[] frame){
        byte parity = 0;
        for (byte b: frame) {
            // p(a,b) = [p(a) + p(b)] mod 2 : odd + odd = even, odd + even = odd, even + even = even.
                parity = (byte) (parity ^ checkParity(b));
        }
        return parity;
    }


    // ===============================================================
    // DATA MEMBERS
    // ===============================================================



    // ===============================================================
    // The start tag, stop tag, and the escape tag.
    private final byte startTag  = (byte)'{';
    private final byte stopTag   = (byte)'}';
    private final byte escapeTag = (byte)'\\';
    // ===============================================================
}