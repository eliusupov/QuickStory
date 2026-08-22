package server;

import constants.inventory.ItemConstants;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the scroll sell prices. Every scroll in Item.wz ships {@code info/price = 1}, so every one of
 * them sold to an NPC for a single meso; {@link ItemConstants#scrollSellPrice(int)} prices
 * them off {@code info/success} instead.
 *
 * <p>Reads 0204.img.xml straight off disk rather than through {@link provider.wz.WZFiles}, so it is
 * a plain {@code *Test} that runs in the normal suite - see the note on {@code TabletScrollRealLoad}
 * for why the wz-loading siblings cannot be.
 *
 * <pre>
 *   mvnw.cmd test -Dtest=ScrollSellPriceTest
 * </pre>
 */
class ScrollSellPriceTest {
    private static final Path SCROLLS = Path.of("wz", "Item.wz", "Consume", "0204.img.xml");

    @Test
    void theOwnersFiveTiersArePinned() {
        assertEquals(35_000, ItemConstants.scrollSellPrice(100));
        assertEquals(150_000, ItemConstants.scrollSellPrice(70));
        assertEquals(100_000, ItemConstants.scrollSellPrice(60));
        assertEquals(400_000, ItemConstants.scrollSellPrice(30));
        assertEquals(250_000, ItemConstants.scrollSellPrice(10));
    }

    @Test
    void oddsOutsideTheTableTakeTheNearestTier() {
        // Clean Slate 1/3/5% and Balrog's Twilight 5% sit below the lowest tier: clamp, do not
        // extrapolate. The curve peaks at 30% and falls to 10%, so extrapolating inverts it.
        assertEquals(250_000, ItemConstants.scrollSellPrice(5));
        assertEquals(250_000, ItemConstants.scrollSellPrice(1));
        assertEquals(35_000, ItemConstants.scrollSellPrice(100_000));
        // Ties break toward the lower odds, deterministically.
        assertEquals(200_000, ItemConstants.scrollSellPrice(55));
    }

    @Test
    void everyScrollInTheTreeIsWorthMoreThanAMeso() throws Exception {
        assertTrue(Files.isReadable(SCROLLS), SCROLLS + " is missing");
        NodeList items = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(SCROLLS.toFile()).getDocumentElement().getChildNodes();

        int seen = 0;
        for (int i = 0; i < items.getLength(); i++) {
            if (items.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) items.item(i);
            int itemId = Integer.parseInt(item.getAttribute("name"));
            // Tablets state no flat success; getItemPriceData reads them as their 70% base rate.
            String success = successOf(item);
            int price = ItemConstants.scrollSellPrice(
                    success == null ? 70 : Integer.parseInt(success));
            assertTrue(price >= 35_000, itemId + " (success " + success + ") sells for " + price);
            seen++;
        }
        assertEquals(788, seen, "0204.img changed size; re-check the tier table covers it");
    }

    private static String successOf(Element item) {
        NodeList info = item.getElementsByTagName("*");
        for (int i = 0; i < info.getLength(); i++) {
            Element e = (Element) info.item(i);
            if ("success".equals(e.getAttribute("name")) && !"imgdir".equals(e.getTagName())) {
                return e.getAttribute("value");   // 02040016 states it as a string, not an int
            }
        }
        return null;
    }
}
