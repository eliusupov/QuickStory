package server;

import constants.inventory.ItemConstants;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the scroll sell prices. Every scroll in Item.wz ships {@code info/price = 1}, so every one of
 * them sold to an NPC for a single meso; {@link ItemConstants#scrollSellPrice(int, int)} prices them
 * off {@code info/success} instead, with the GM scrolls flat at 750k.
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
    private static final int NOT_GM = 2040001;   // a plain 60% scroll, so only the tier applies

    @Test
    void theOwnersFiveTiersArePinned() {
        assertEquals(35_000, ItemConstants.scrollSellPrice(NOT_GM, 100));
        assertEquals(150_000, ItemConstants.scrollSellPrice(NOT_GM, 70));
        assertEquals(100_000, ItemConstants.scrollSellPrice(NOT_GM, 60));
        assertEquals(400_000, ItemConstants.scrollSellPrice(NOT_GM, 30));
        assertEquals(250_000, ItemConstants.scrollSellPrice(NOT_GM, 10));
    }

    @Test
    void oddsOutsideTheTableTakeTheNearestTier() {
        // Clean Slate 1/3/5% and Balrog's Twilight 5% sit below the lowest tier: clamp, do not
        // extrapolate. The curve peaks at 30% and falls to 10%, so extrapolating inverts it.
        assertEquals(250_000, ItemConstants.scrollSellPrice(NOT_GM, 5));
        assertEquals(250_000, ItemConstants.scrollSellPrice(NOT_GM, 1));
        assertEquals(35_000, ItemConstants.scrollSellPrice(NOT_GM, 100_000));
        // Ties break toward the lower odds, deterministically.
        assertEquals(200_000, ItemConstants.scrollSellPrice(NOT_GM, 55));
    }

    /**
     * The GM scrolls are not a hand-picked list: they are exactly the 100% scrolls whose stats are
     * a 10% scroll's stats. This re-derives that set from Item.wz and demands the constant match,
     * so a wz change cannot silently leave one of them at the 100% tier.
     */
    @Test
    void theGmScrollsAreTheHundredsThatHitLikeTens() throws Exception {
        Map<Integer, Element> items = scrolls();
        Map<String, Integer> tenPercent = new LinkedHashMap<>();
        for (Map.Entry<Integer, Element> e : items.entrySet()) {
            if ("10".equals(childValue(e.getValue(), "success"))) {
                tenPercent.putIfAbsent(familyKey(e.getKey(), e.getValue()), e.getKey());
            }
        }

        TreeSet<Integer> derived = new TreeSet<>();
        for (Map.Entry<Integer, Element> e : items.entrySet()) {
            String success = childValue(e.getValue(), "success");
            String key = familyKey(e.getKey(), e.getValue());
            if ("100".equals(success) && !incStats(e.getValue()).isEmpty() && tenPercent.containsKey(key)) {
                derived.add(e.getKey());
            }
        }

        assertEquals(29, derived.size(), "GM scroll count changed: " + derived);
        for (int itemId : derived) {
            assertEquals(750_000, ItemConstants.scrollSellPrice(itemId, 100),
                    itemId + " is a 100% with 10% stats but is not priced as a GM scroll");
        }
        // And nothing else is: the plain 100% of the same family stays at the 100% tier.
        assertEquals(35_000, ItemConstants.scrollSellPrice(2043000, 100));
        assertEquals(35_000, ItemConstants.scrollSellPrice(2043023, 100));
    }

    @Test
    void everyScrollInTheTreeIsWorthMoreThanAMeso() throws Exception {
        Map<Integer, Element> items = scrolls();
        for (Map.Entry<Integer, Element> e : items.entrySet()) {
            // Tablets state no flat success; getItemPriceData reads them as their 70% base rate.
            String success = childValue(e.getValue(), "success");
            int price = ItemConstants.scrollSellPrice(e.getKey(),
                    success == null ? 70 : Integer.parseInt(success));
            assertTrue(price >= 35_000, e.getKey() + " (success " + success + ") sells for " + price);
        }
        assertEquals(788, items.size(), "0204.img changed size; re-check the tier table covers it");
    }

    private static Map<Integer, Element> scrolls() throws Exception {
        assertTrue(Files.isReadable(SCROLLS), SCROLLS + " is missing");
        NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(SCROLLS.toFile()).getDocumentElement().getChildNodes();
        Map<Integer, Element> out = new LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) nodes.item(i);
                out.put(Integer.parseInt(item.getAttribute("name")), item);
            }
        }
        return out;
    }

    /** Same hundred-block, same inc stats - the two halves of "same scroll, different odds". */
    private static String familyKey(int itemId, Element item) {
        return (itemId / 100) + incStats(item).toString();
    }

    private static Map<String, String> incStats(Element item) {
        Map<String, String> out = new TreeMap<>();
        NodeList all = item.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.getAttribute("name").startsWith("inc")) {
                out.put(e.getAttribute("name"), e.getAttribute("value"));
            }
        }
        return out;
    }

    private static String childValue(Element item, String name) {
        NodeList all = item.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (name.equals(e.getAttribute("name")) && !"imgdir".equals(e.getTagName())) {
                return e.getAttribute("value");   // 02040016 states success as a string, not an int
            }
        }
        return null;
    }
}
