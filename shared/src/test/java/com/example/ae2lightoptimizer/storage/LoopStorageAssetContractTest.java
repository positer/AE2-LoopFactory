package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LoopStorageAssetContractTest {
    private static final Path TEXTURES = Path.of(
            "src/main/resources/assets/ae2lightoptimizer/textures/item");
    private static final Path DRIVE_MODELS = Path.of(
            "src/main/resources/assets/ae2lightoptimizer/models/block/drive/cells");

    private static final List<TierAsset> TIERS = List.of(
            new TierAsset("1k", "1k", false,
                    "2EDF8D2CAC4A932F929494CA1A2001AEAD608CC878D44C6AA79F4B07DB1D0A50"),
            new TierAsset("4k", "4k", false,
                    "65CAD27E4937581F852837F75B762F27501F3A30F924FA4FD3B92C620223F5EA"),
            new TierAsset("16k", "16k", false,
                    "02E982D6A66297985BCDFE9EEFD640D057D9A9FDEFE33B8BA264142A9216B82A"),
            new TierAsset("64k", "64k", false,
                    "EE18B996A6F764DA19EEA5E16ECA29B5912C978DC407DCF9C95A4631BDDA4213"),
            new TierAsset("256k", "256k", false,
                    "1F65958486B13DBD66B93F524ECEA5C471A12313E1525534C0BB0F1C68C8F6CC"),
            new TierAsset("1m", "1k", true,
                    "9DB6AF7141F9A616E400076B67A18049443A772D655ABDA30239FCD94844364E"),
            new TierAsset("4m", "4k", true,
                    "384663DD7616CDC0EFCA03BEAECA967FF5C59E6D5C81B4221E9A4F65BDC83B3F"),
            new TierAsset("16m", "16k", true,
                    "1C0D5EE00D2DE66B12AD1491E6EC6F974FC2E4A50A85B5FAC7B85C030D498A8B"),
            new TierAsset("64m", "64k", true,
                    "70A1B32233F4E21D98579205A30BE8726305EBBF18E6687A6CE35214DE9F04B0"),
            new TierAsset("256m", "256k", true,
                    "8E63F6BE4F0E4DC2944F70002438E34216161923D65D6F3DD03DFE820EA42349"));

    private static final Map<Integer, Integer> K_SHELL = Map.ofEntries(
            rgb(65, 63, 84, 48, 48, 48),
            rgb(77, 77, 103, 58, 58, 58),
            rgb(105, 109, 136, 82, 82, 82),
            rgb(135, 143, 165, 110, 110, 110),
            rgb(154, 159, 180, 126, 126, 126),
            rgb(173, 176, 196, 143, 143, 143),
            rgb(203, 204, 212, 170, 170, 170),
            rgb(222, 223, 227, 190, 190, 190),
            rgb(242, 242, 242, 214, 214, 214));
    private static final Map<Integer, Integer> M_SHELL = Map.ofEntries(
            rgb(65, 63, 84, 13, 13, 13),
            rgb(77, 77, 103, 21, 21, 21),
            rgb(105, 109, 136, 39, 39, 39),
            rgb(135, 143, 165, 57, 57, 57),
            rgb(154, 159, 180, 67, 67, 67),
            rgb(173, 176, 196, 77, 77, 77),
            rgb(203, 204, 212, 93, 93, 93),
            rgb(222, 223, 227, 104, 104, 104),
            rgb(242, 242, 242, 115, 115, 115));
    private static final Map<Integer, Integer> INFINITE = Map.ofEntries(
            rgb(65, 63, 84, 22, 13, 29),
            rgb(77, 77, 103, 30, 19, 41),
            rgb(105, 109, 136, 43, 28, 58),
            rgb(135, 143, 165, 57, 39, 76),
            rgb(154, 159, 180, 68, 47, 89),
            rgb(173, 176, 196, 80, 57, 104),
            rgb(203, 204, 212, 100, 75, 127),
            rgb(222, 223, 227, 119, 93, 147),
            rgb(242, 242, 242, 145, 116, 174),
            rgb(45, 105, 225, 174, 118, 238),
            rgb(56, 148, 255, 210, 160, 255),
            rgb(64, 193, 255, 240, 214, 255));

    @Test
    void copiesEverySuppliedCoreTextureByteForByte() throws Exception {
        for (TierAsset tier : TIERS) {
            assertEquals(tier.coreSha256(), sha256(TEXTURES.resolve(
                    tier.id() + "_loop_storage_core.png")), tier.id());
        }
    }

    @Test
    void recolorsOnlyTheRequestedPixelsWithoutChangingGeometry() throws Exception {
        assertRecolor(
                classpathImage("assets/ae2/textures/item/item_cell_housing.png"),
                ImageIO.read(TEXTURES.resolve("loop_storage_cell_housing.png").toFile()),
                K_SHELL,
                "housing");

        for (TierAsset tier : TIERS) {
            assertRecolor(
                    classpathImage("assets/ae2/textures/item/item_storage_cell_"
                            + tier.baseTier() + ".png"),
                    ImageIO.read(TEXTURES.resolve(tier.id() + "_loop_storage_cell.png").toFile()),
                    tier.mTier() ? M_SHELL : K_SHELL,
                    tier.id());
        }

        assertRecolor(
                classpathImage("assets/ae2/textures/item/item_storage_cell_1k.png"),
                ImageIO.read(TEXTURES.resolve("infinite_loop_storage_cell.png").toFile()),
                INFINITE,
                "infinite");
    }

    @Test
    void portableCellsReuseNativeLayersAndOnlyApplyTheLoopPalette() throws Exception {
        BufferedImage nativeHousing = classpathImage(
                "assets/ae2/textures/item/portable_cell_item_housing.png");
        assertRecolor(nativeHousing,
                ImageIO.read(TEXTURES.resolve("portable_loop_storage_cell_housing_k.png").toFile()),
                K_SHELL, "portable k housing");
        assertRecolor(nativeHousing,
                ImageIO.read(TEXTURES.resolve("portable_loop_storage_cell_housing_m.png").toFile()),
                M_SHELL, "portable m housing");
        assertRecolor(nativeHousing,
                ImageIO.read(TEXTURES.resolve("portable_loop_storage_cell_housing_infinite.png").toFile()),
                INFINITE, "portable infinite housing");

        for (TierAsset tier : TIERS) {
            assertArrayEquals(
                    classpathBytes("assets/ae2/textures/item/portable_cell_side_"
                            + tier.baseTier() + ".png"),
                    Files.readAllBytes(TEXTURES.resolve(
                            "portable_" + tier.id() + "_loop_storage_cell_side.png")),
                    tier.id() + " portable side");
        }
        assertRecolor(
                classpathImage("assets/ae2/textures/item/portable_cell_side_1k.png"),
                ImageIO.read(TEXTURES.resolve(
                        "portable_infinite_loop_storage_cell_side.png").toFile()),
                INFINITE,
                "portable infinite side");
    }

    @Test
    void copiesNativeAe2DriveModelsWithoutAddingGeometry() throws Exception {
        for (TierAsset tier : TIERS) {
            assertArrayEquals(
                    nativeDriveModel(tier.baseTier()),
                    Files.readAllBytes(DRIVE_MODELS.resolve(
                            tier.id() + "_loop_storage_cell.json")),
                    tier.id());
        }
        assertArrayEquals(
                nativeDriveModel("1k"),
                Files.readAllBytes(DRIVE_MODELS.resolve("infinite_loop_storage_cell.json")),
                "infinite");
    }

    private static void assertRecolor(
            BufferedImage source,
            BufferedImage actual,
            Map<Integer, Integer> lut,
            String label) {
        assertNotNull(actual, label);
        assertEquals(source.getWidth(), actual.getWidth(), label + " width");
        assertEquals(source.getHeight(), actual.getHeight(), label + " height");
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int before = source.getRGB(x, y);
                int expectedRgb = lut.getOrDefault(before & 0xFFFFFF, before & 0xFFFFFF);
                int expected = (before & 0xFF000000) | expectedRgb;
                assertEquals(expected, actual.getRGB(x, y),
                        label + " pixel (" + x + "," + y + ")");
            }
        }
    }

    private static BufferedImage classpathImage(String path) throws IOException {
        try (InputStream stream = LoopStorageAssetContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path);
            return image;
        }
    }

    private static byte[] classpathBytes(String path) throws IOException {
        try (InputStream stream = LoopStorageAssetContractTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        }
    }

    private static byte[] nativeDriveModel(String tier) throws IOException {
        String legacy = "assets/ae2/models/block/drive/cells/" + tier + "_item_cell.json";
        String modern = "assets/ae2/models/block/drive_" + tier + "_item_cell.json";
        ClassLoader loader = LoopStorageAssetContractTest.class.getClassLoader();
        InputStream legacyStream = loader.getResourceAsStream(legacy);
        InputStream selected = legacyStream != null ? legacyStream : loader.getResourceAsStream(modern);
        assertNotNull(selected, "native AE2 drive model for " + tier);
        try (selected) {
            return selected.readAllBytes();
        }
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return HexFormat.of().withUpperCase().formatHex(digest);
    }

    private static Map.Entry<Integer, Integer> rgb(
            int sourceRed,
            int sourceGreen,
            int sourceBlue,
            int targetRed,
            int targetGreen,
            int targetBlue) {
        return Map.entry(
                (sourceRed << 16) | (sourceGreen << 8) | sourceBlue,
                (targetRed << 16) | (targetGreen << 8) | targetBlue);
    }

    private record TierAsset(String id, String baseTier, boolean mTier, String coreSha256) {
    }
}
