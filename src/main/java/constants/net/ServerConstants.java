package constants.net;

public class ServerConstants {

    // GMS v84 is the only supported client version. This field drives:
    //   - the version field of the hello packet (ServerChannelInitializer -> PacketCreator.getHello)
    //   - BOTH AES-OFB cipher keys (ClientCyphers: send = 0xFFFF - VERSION, recv = VERSION)
    //   - the startup banner (Server.java)
    // OpcodeTable loads the matching bundled v84 table; there is no runtime version switch.
    public static final short VERSION = 84;

    //Debug Variables
    public static int[] DEBUG_VALUES = new int[10];             // Field designed for packet testing purposes
}
