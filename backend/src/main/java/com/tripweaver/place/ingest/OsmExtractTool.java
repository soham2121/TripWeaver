package com.tripweaver.place.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * Command-line step 1 of the catalogue pipeline: PBF → compact destination extract (JSON).
 *
 * <pre>
 * cd backend
 * ./mvnw -q compile exec:java -Dexec.mainClass=com.tripweaver.place.ingest.OsmExtractTool \
 *   -Dexec.args="../data/raw/western-zone-261122.osm.pbf ../data/sample/goa-osm-extract.json"
 * </pre>
 *
 * Defaults are for Goa: bbox lat 14.85–15.85, lon 73.60–74.40, boundary relation name=Goa, admin_level=4.
 * Konkan corridor (Mumbai–Goa road trips): {@code 15.60,72.75,19.10,74.10 -Goa 4} = the bbox minus Goa. (The
 * Maharashtra relation cannot be used: the regional extract clips its far border, so its mainland ring never closes.)
 */
public final class OsmExtractTool {

    private OsmExtractTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 5) {
            System.err.println("usage: OsmExtractTool <input.osm.pbf> <output.json>"
                    + " [minLat,minLon,maxLat,maxLon <boundaryName> <adminLevel>]");
            System.exit(2);
        }
        Path in = Path.of(args[0]), out = Path.of(args[1]);
        OsmPbfExtractor.Bbox bbox = new OsmPbfExtractor.Bbox(14.85, 73.60, 15.85, 74.40);
        String boundary = "Goa", level = "4";
        if (args.length == 5) {
            String[] b = args[2].split(",");
            bbox = new OsmPbfExtractor.Bbox(
                    Double.parseDouble(b[0]),
                    Double.parseDouble(b[1]),
                    Double.parseDouble(b[2]),
                    Double.parseDouble(b[3]));
            boundary = args[3];
            level = args[4];
        }
        long t0 = System.nanoTime();
        // "-Name" keeps the bbox minus that boundary (a corridor next to a destination), e.g. -Goa.
        boolean exclude = boundary.startsWith("-");
        OsmExtract ex = OsmPbfExtractor.extract(in, bbox, exclude ? boundary.substring(1) : boundary, level);
        if (exclude) ex = bboxMinus(ex, bbox, "bbox minus " + boundary.substring(1));
        Files.createDirectories(out.toAbsolutePath().getParent());
        JsonMapper.builder().build().writeValue(out.toFile(), ex);
        System.out.printf(
                "features=%d settlements=%d boundaryOuterRings=%d innerRings=%d timestamp=%s in %.1fs%n",
                ex.features().size(),
                ex.settlements().size(),
                ex.boundaryOuters().size(),
                ex.boundaryInners().size(),
                ex.sourceTimestamp(),
                (System.nanoTime() - t0) / 1e9);
    }

    /** The bbox as the only outer ring, with every outer ring of {@code ex}'s boundary as a hole. */
    static OsmExtract bboxMinus(OsmExtract ex, OsmPbfExtractor.Bbox b, String name) {
        double[][] rect = {
            {b.minLat(), b.minLon()},
            {b.minLat(), b.maxLon()},
            {b.maxLat(), b.maxLon()},
            {b.maxLat(), b.minLon()},
            {b.minLat(), b.minLon()}
        };
        return new OsmExtract(
                ex.sourceFile(),
                ex.sourceTimestamp(),
                name,
                List.<double[][]>of(rect),
                ex.boundaryOuters(),
                ex.features(),
                ex.settlements());
    }
}
