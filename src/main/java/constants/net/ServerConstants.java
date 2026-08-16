package constants.net;

public class ServerConstants {

    // Server Version. Must match the client: 83 = GMS v83, 84 = GMS v84.
    // This one field drives three things, so it cannot be changed in isolation:
    //   - the version field of the hello packet (ServerChannelInitializer -> PacketCreator.getHello)
    //   - BOTH AES-OFB cipher keys (ClientCyphers: send = 0xFFFF - VERSION, recv = VERSION)
    //   - the startup banner (Server.java)
    // The opcode table is separate and is selected with -Dopcode-version=NN (default 83), so
    // changing this alone would leave the server speaking v83 opcodes over a v84 handshake.
    // Run the v84 client against this with -Dopcode-version=84. See ticket 29 (cutover).
    public static final short VERSION = 84;

    //Debug Variables
    public static int[] DEBUG_VALUES = new int[10];             // Field designed for packet testing purposes
}
