# CRCDataLinkLayer

A Java-based implementation of a data link layer protocol that utilizes CRC (Cyclic Redundancy Check) for error detection. This project handles framing using start/stop tags, byte packing, and provides robust methods for frame extraction and error handling. The implementation allows flexibility in using different CRC generators.

---

## Features

1. **CRC Error Detection**
   - Supports configurable CRC generators (e.g., CRC-16 and CRC-32).
   - Performs byte-level long division to calculate CRC values.

2. **Framing with Metadata Tags**
   - Uses start (`{`), stop (`}`), and escape (`\`) tags to delimit frames.
   - Escapes metadata tags within the data to ensure correct framing.

3. **Flexible Data Handling**
   - Splits data into frames of 8 bytes (or fewer).
   - Dynamically appends CRC to frames.

4. **Robust Frame Processing**
   - Detects and discards incomplete or corrupted frames.
   - Handles edge cases like corrupted start/stop tags.

---

## Technologies Used

- **Java**: The implementation leverages core Java for byte-level operations and data structures like `Queue` and `LinkedList`.

---

## How It Works

1. **Sending Data**
   - Data is divided into chunks of up to 8 bytes.
   - CRC is calculated for each chunk and appended.
   - Frames are created with start, stop, and escape tags.
   - Frames are transmitted byte by byte.

2. **Receiving Data**
   - Incoming bytes are buffered until a complete frame (start to stop) is identified.
   - Escaped metadata tags are handled correctly.
   - CRC validation ensures data integrity. Frames failing CRC validation are discarded.

---

## Setup and Usage

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd <repository-folder>
   ```

2. Compile the Java code:
   ```bash
   javac CRCDataLinkLayer.java
   ```

3. Run the program:
   ```bash
   java CRCDataLinkLayer
   ```

4. Change the CRC generator by modifying the `generatorInUse` constant at the top of the `CRCDataLinkLayer` class. For example:
   ```java
   private final int CRC16generator = 0x1021;
   private final int CRC32generator = 0x04C11DB7;

   int generatorInUse = CRC16generator; // Use CRC-16
   ```

---

## Key Methods

1. `send(byte[] data)`
   - Prepares and sends data by framing and appending CRC.

2. `createFrame(byte[] data)`
   - Frames the data with metadata tags and appends CRC.

3. `processFrame()`
   - Extracts and validates data from received frames.

4. `makeCRC(byte[] message, int generator)`
   - Generates CRC for a given data array using the specified generator.

5. `byteLongDivision(byte[] messageWithAppendedZeros, int generator)`
   - Implements long division at the byte level to calculate CRC.

---

## Challenges Addressed

- **Corrupted Tags**: Ensures frames with corrupted start/stop tags are discarded.
- **Efficient Framing**: Balances processing overhead with data size constraints.
- **Flexibility**: Allows easy switching between CRC-16 and CRC-32 generators.

---

## Author

- **Aime Cesaire Mugishawayo**
  - Email: [cmugishawayo25@amherst.edu](mailto:cmugishawayo25@amherst.edu)

---

## Future Enhancements

1. Add support for other CRC generators (e.g., CRC-8).
2. Optimize frame processing for higher throughput.
3. Implement additional error-correction techniques.

---

## License

This project is licensed under the Apache-2.0 License. See the LICENSE file for details.

