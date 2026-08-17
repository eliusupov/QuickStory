package server.maps;

import client.Character;
import client.Client;
import client.Job;
import client.inventory.Pet;
import net.packet.Packet;
import net.server.Server;
import net.server.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.TimerManager;
import tools.PacketCreator;

import java.awt.Point;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An Evan walking into a map must be served their own dragon.
 *
 * <p>{@link MapleMap#addPlayer} used to register the dragon <em>after</em>
 * {@code sendObjectPlacement}, and {@link MapleMap#spawnDragon} broadcasts to everyone
 * <em>except</em> the owner, so the arriving Evan got the object list before the dragon was in it
 * and was then excluded from the only packet carrying it: Mir vanished for its owner on every
 * portal, for everyone else it stayed. Login was unaffected only because the {@link Dragon}
 * constructor sends the spawn straight to the owner.
 */
class DragonMapEntryTest {

    private static final int EVAN_ID = 1701;

    @Test
    void arrivingEvanIsServedTheirOwnDragonExactlyOnce() {
        try (MockedStatic<Server> server = mockStatic(Server.class);
             MockedStatic<TimerManager> timers = mockStatic(TimerManager.class)) {
            Server serverInstance = mock(Server.class);
            server.when(Server::getInstance).thenReturn(serverInstance);
            when(serverInstance.getWorld(anyInt())).thenReturn(mock(World.class));
            timers.when(TimerManager::getInstance).thenReturn(mock(TimerManager.class));

            Client client = mock(Client.class);
            Character evan = mock(Character.class);
            when(evan.getClient()).thenReturn(client);
            when(client.getPlayer()).thenReturn(evan);
            when(evan.getId()).thenReturn(EVAN_ID);
            when(evan.getPosition()).thenReturn(new Point(-95, 275)); // Evan's own room
            when(evan.getPets()).thenReturn(new Pet[3]);
            when(evan.getJob()).thenReturn(Job.EVAN2);   // the job change that hands out Mir

            Dragon dragon = new Dragon(evan);   // as PlayerLoggedinHandler does, once, at login
            when(evan.getDragon()).thenReturn(dragon);
            clearInvocations(client);           // that login spawn is not what this test counts

            MapleMap map = new MapleMap(100030100, 0, 0, 0, 1);
            map.setOnFirstUserEnter("");
            map.setOnUserEnter("");
            map.addPlayer(evan);

            assertEquals(1, countSpawnDragons(client, dragon),
                    "the arriving Evan must be sent their dragon once - never zero (Mir despawns on "
                            + "every portal), never twice (duplicate spawn for one object id)");
        }
    }

    private static int countSpawnDragons(Client client, Dragon dragon) {
        ArgumentCaptor<Packet> sent = ArgumentCaptor.forClass(Packet.class);
        verify(client, org.mockito.Mockito.atLeast(0)).sendPacket(sent.capture());

        byte[] expected = PacketCreator.spawnDragon(dragon).getBytes();
        return (int) sent.getAllValues().stream()
                .filter(p -> Arrays.equals(expected, p.getBytes()))
                .count();
    }
}
