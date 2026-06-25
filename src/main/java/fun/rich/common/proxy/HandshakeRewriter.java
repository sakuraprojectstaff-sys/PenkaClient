package fun.rich.common.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class HandshakeRewriter {
    private static final int HANDSHAKE_PACKET_ID = 0;

    private HandshakeRewriter() {
    }

    public static byte[] rewriteHandshakePacket(byte[] frameData, String replacementHost) throws IOException {
        return rewriteHandshakePacket(frameData, replacementHost, -1);
    }

    public static byte[] rewriteHandshakePacket(byte[] frameData, String replacementHost, int replacementPort) throws IOException {
        if ((replacementHost == null || replacementHost.isEmpty()) && replacementPort <= 0) {
            return frameData;
        }

        ByteArrayInputStream in = new ByteArrayInputStream(frameData);
        int packetId = MCVarInt.read(in);
        if (packetId != HANDSHAKE_PACKET_ID) {
            return frameData;
        }

        int protocolVersion = MCVarInt.read(in);

        int originalHostLength = MCVarInt.read(in);
        byte[] originalHostBytes = readExact(in, originalHostLength);

        int portHigh = in.read();
        int portLow = in.read();
        if (portHigh < 0 || portLow < 0) {
            throw new IOException("Invalid handshake packet: missing port");
        }

        int originalPort = ((portHigh & 0xFF) << 8) | (portLow & 0xFF);
        int nextState = MCVarInt.read(in);

        byte[] outHostBytes;
        if (replacementHost != null && !replacementHost.isEmpty()) {
            byte[] replacementHostBytes = replacementHost.getBytes(StandardCharsets.UTF_8);

            int nullIndex = indexOfZero(originalHostBytes);
            if (nullIndex >= 0) {
                byte[] suffix = Arrays.copyOfRange(originalHostBytes, nullIndex, originalHostBytes.length);
                outHostBytes = new byte[replacementHostBytes.length + suffix.length];
                System.arraycopy(replacementHostBytes, 0, outHostBytes, 0, replacementHostBytes.length);
                System.arraycopy(suffix, 0, outHostBytes, replacementHostBytes.length, suffix.length);
            } else {
                outHostBytes = replacementHostBytes;
            }
        } else {
            outHostBytes = originalHostBytes;
        }

        int outPort = replacementPort > 0 ? replacementPort : originalPort;
        if (outPort < 0 || outPort > 65535) {
            throw new IOException("Invalid replacement port: " + outPort);
        }

        ByteArrayOutputStream packetOut = new ByteArrayOutputStream(frameData.length + outHostBytes.length + 8);
        MCVarInt.write(packetOut, HANDSHAKE_PACKET_ID);
        MCVarInt.write(packetOut, protocolVersion);
        MCVarInt.write(packetOut, outHostBytes.length);
        packetOut.write(outHostBytes);
        packetOut.write((outPort >>> 8) & 0xFF);
        packetOut.write(outPort & 0xFF);
        MCVarInt.write(packetOut, nextState);

        byte[] remainder = in.readAllBytes();
        if (remainder.length > 0) {
            packetOut.write(remainder);
        }

        return packetOut.toByteArray();
    }

    public static byte[] wrapFrame(byte[] packetData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(packetData.length + 5);
        MCVarInt.write(out, packetData.length);
        out.write(packetData);
        return out.toByteArray();
    }

    private static byte[] readExact(ByteArrayInputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int read = in.read(data);
        if (read != len) {
            throw new IOException("Unexpected end of handshake packet");
        }
        return data;
    }

    private static int indexOfZero(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0) {
                return i;
            }
        }
        return -1;
    }
}