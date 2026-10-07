package client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPeerResetTest {
    @Test
    void onlyPeerResetIsTreatedAsNormalDisconnect() {
        assertTrue(Client.isPeerReset(new SocketException("Connection reset")));
        assertFalse(Client.isPeerReset(new SocketException("Broken pipe")));
        assertFalse(Client.isPeerReset(new IOException("Connection reset")));
        assertFalse(Client.isPeerReset(new IllegalStateException("Connection reset")));
    }
}
