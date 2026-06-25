package fun.rich.common.proxy;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class MCVarInt {
    private MCVarInt() {
    }

    public static int read(InputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) {
                throw new EOFException("Unexpected end of stream while reading VarInt");
            }
            int value = read & 0x7F;
            result |= value << (7 * numRead);
            numRead++;
            if (numRead > 5) {
                throw new IOException("VarInt is too big");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    public static int read(byte[] data, int[] indexRef) throws IOException {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            if (indexRef[0] >= data.length) {
                throw new EOFException("Unexpected end of buffer while reading VarInt");
            }
            read = data[indexRef[0]++] & 0xFF;
            int value = read & 0x7F;
            result |= value << (7 * numRead);
            numRead++;
            if (numRead > 5) {
                throw new IOException("VarInt is too big");
            }
        } while ((read & 0x80) != 0);
        return result;
    }

    public static void write(OutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value & 0x7F);
    }

    public static byte[] write(int value) {
        byte[] tmp = new byte[5];
        int i = 0;
        do {
            byte part = (byte)(value & 0x7F);
            value >>>= 7;
            if (value != 0) {
                part |= (byte)0x80;
            }
            tmp[i++] = part;
        } while (value != 0);

        byte[] out = new byte[i];
        System.arraycopy(tmp, 0, out, 0, i);
        return out;
    }

    public static int sizeOf(int value) {
        int size = 1;
        while ((value & 0xFFFFFF80) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }
}