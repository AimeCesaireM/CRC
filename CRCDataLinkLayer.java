// =============================================================================
// IMPORTS

import java.util.*;
// =============================================================================


// =============================================================================
/**
 * @file   ParityDataLinkLayer.java
 * @author Aime Cesaire Mugishawayo (cmugishawayo25@amherst.edu)
 * @date   September 2023,
 *
 * TO GRADER: THE GENERATORS ARE INITIALIZED AS CONSTANTS AT THE TOP OF THE CLASS.
 *  FEEL FREE TO TRY DIFFERENT GENERATORS BY CHANGING THE VALUE OF "generatorInUse"


 * A data link layer that uses start/stop tags and byte packing to frame the
 * data, and that performs no error management.
 */
public class CRCDataLinkLayer extends DataLinkLayer {
// =============================================================================
    private final int CRC16generator = 0x1021 ;
    private final int CRC32generator = 0x04C11DB7;

    // Change the value of generatorInUse to any generator you want to try.
    int generatorInUse = CRC16generator;



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

            // send each frame on its way.
            for (byte frameByte : framedData) {
                transmit(frameByte);
            }
        }

    }
    protected byte[] createFrame (byte[] data) {
//        System.out.println("[=] Data to Frame: " + new String(data));

        // the crc byte array is appended at the end of the data array
        byte[] crc = makeCRC(data, generatorInUse);
        byte[] dataWithCRC = new byte[data.length + crc.length];

        System.arraycopy(data, 0, dataWithCRC, 0, data.length);
        System.arraycopy(crc, 0, dataWithCRC, data.length, crc.length );

        Queue<Byte> framingData = new LinkedList<>();

        // Begin with the start tag.
        framingData.add(startTag);

        // Add each byte of original data.
        for (int i = 0; i < dataWithCRC.length; i += 1) {

            // If the current data byte is itself a metadata tag, then precede
            // it with an escape tag.
            byte currentByte = dataWithCRC[i];
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
            System.out.println("processFrame(): Got whole frame!");
        }

        if (extractedBytes.size() == 0){
            System.err.println("[-] Empty Frame Was Received");
            return null;
        }

        byte[] extractedData = new byte[extractedBytes.size()];
        int                j = 0;
        i = extractedBytes.iterator();
        while (i.hasNext()) {
            extractedData[j] = i.next();
            if (debug) {
                System.out.printf("processFrame():\tbyte[%d] = %c\n",
                        j,
                        extractedData[j]);
            }
            j += 1;
        }


        // Code to handle the check of the CRC
        int quotient = byteLongDivision(extractedData, generatorInUse);
        int appendedBytes = getNumOfBytesToAppend(generatorInUse);

//        System.out.println("[-] Debug here: extracted data length =  " + extractedData.length
//                + "; #of appended bytes = " + appendedBytes);


        /**  If the low noise medium corrupts a start tag or stop tag, the length of extracted data might be affected,
         e.g think of a scenario where right after a bit flips and makes a byte look like a start tag, followed by
         another byte of uncorrupted data and then followed by the actual stop tag.
         or similarly a byte has a flipped bit that makes it look like a stop tag.


         that will trigger an error here if extracted data length is less than appended bytes. so to handle it,
         introduce a check.
         **/

        if (extractedData.length < appendedBytes){
            System.err.println("[-] Start or stop tag corrupted.");
            System.err.println("[-] Thrown out frame; " + new String(extractedData));
            return null;
        }
        byte[] dataMinusCRC = new byte[extractedData.length - appendedBytes];


        System.arraycopy(extractedData, 0, dataMinusCRC, 0, dataMinusCRC.length);

        if (quotient == 0){
            return dataMinusCRC;
        }
        else{
            System.err.println("[-] CRC Remainder :" + quotient + " should be 0; indicates corrupted data.");
            System.err.println("[-] Thrown out frame: " + new String(dataMinusCRC) + " \n");
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

    private byte[] makeCRC(byte[] message, int generator){
        byte[] messageWithAppendedZeros = appendZerosToMessage(message, generator);
        int quotient = byteLongDivision(messageWithAppendedZeros, generator);

        return returnCRCAsByteArray(quotient, generator);
    }

    private int getNumOfBits (int parameter){
        int bitCount = 0;
        while (parameter != 0){
            parameter >>>= 1;
            ++ bitCount;
        }
        return bitCount;
    }

    private int getNumOfBytesToAppend(int generator){
        int numOfBitsToAppend = getNumOfBits(generator) - 1;
        return (int) Math.ceil((double) numOfBitsToAppend/ BITS_PER_BYTE);
    }

    private byte[] appendZerosToMessage(byte[] messageWithoutZeros, int generator){
        int numOfBytesToAppend = getNumOfBytesToAppend(generator);
        byte[] messagesWithAppendedZeros = new byte[messageWithoutZeros.length + numOfBytesToAppend];
        System.arraycopy(messageWithoutZeros, 0, messagesWithAppendedZeros, 0, messageWithoutZeros.length);
        return messagesWithAppendedZeros;
    }

    private int byteLongDivision (byte[] messageWithAppendedZeros, int generator){
        int currentDividend = 0;

        // edge case handled*
        for ( int i = 0; i < messageWithAppendedZeros.length; ++ i ){
            byte currentByte = messageWithAppendedZeros[i];

            //if this is the last byte we make sure that we only treat the byte as only the bits that matter
            // we don't want to go into the last byte more bits than we actually added.
            if (i == messageWithAppendedZeros.length - 1){

                int numOfBitsAppended = getNumOfBits(generator) - 1;
                int numOfBytesAppended = (int) Math.ceil((double) numOfBitsAppended/ BITS_PER_BYTE);
                int insignificantBits = (numOfBytesAppended * BITS_PER_BYTE) - numOfBitsAppended;
                int significantBits = BITS_PER_BYTE - insignificantBits;

                // e.g: if we actually appended  10 zero bits, we append 2 bytes, but at the second byte we get #insignificant bits = 16-10 = 6
                // so #significant bits = 8- 6 = 2; so the last byte is read at position 7 and position 6 only : the 2 bits we want.

                currentDividend = divideCurrentDividend(generator, currentDividend, currentByte, significantBits);
            }
            else{
                currentDividend = divideCurrentDividend(generator, currentDividend, currentByte, BITS_PER_BYTE);
            }
        }
        return currentDividend;
        //at the end
    }

    private int divideCurrentDividend(int generator, int currentDividend, byte currentByte, int significantBits){
        int bitCounter = BITS_PER_BYTE - 1;

        // we go from the most significant bit to the least significant bit as we look for a chunk that can divide our generator

        while (bitCounter >= BITS_PER_BYTE - significantBits){
            currentDividend = currentDividend << 1; // make space for next bit
            int currentBit = (currentByte >> bitCounter) & 1;// grab the next highest significant bit
            currentDividend = currentDividend | currentBit; // bring it down into the dividend

            // if the chunk is big enough to divide our generator
            if (getNumOfBits(currentDividend) >= getNumOfBits(generator)){
                // divide; the chunk now becomes the remainder of that division.
                currentDividend ^= generator;
            }
            -- bitCounter;
        }
        return currentDividend;
    }

    private byte[] returnCRCAsByteArray(int quotient, int generator){
        int numOfBitsAppended = getNumOfBits(generator) - 1;
        int numOfBytesAppended = (int) Math.ceil((double) numOfBitsAppended/ BITS_PER_BYTE);
        int insignificantBits = (numOfBytesAppended * BITS_PER_BYTE) - numOfBitsAppended;

        byte[] byteArray = new byte[numOfBytesAppended];

        // since the quotient must slide in exactly the bits we reserved for it, and not in the
        // extra bits Java forces on us because we're using bytes,
        // we have to handle the edge case of if the last byte of zeros only needs to be written in partially.
        // if we pad our quotient with the same number of insignificant zeros to match the positioning,
        // then we should be fine.

        int quotientPaddedWithZeros = quotient << insignificantBits;

        for (int counter = numOfBytesAppended - 1; counter >= 0; -- counter){

            int rightMostByte = quotientPaddedWithZeros & 0xff; //get the bottom byte
            byteArray[counter] |= rightMostByte;
            quotientPaddedWithZeros >>>= BITS_PER_BYTE; //remove bottom byte
            }
            return byteArray;
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
