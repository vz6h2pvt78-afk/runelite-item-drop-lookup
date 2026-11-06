package com.itemdroplookup.ui;

import java.io.InputStream;
import java.util.Properties;

/** Manual diagnostic text from plugin controls only; no client/account/clipboard reads. */
final class LookupReport
{
    private static final String IDENTITY = readIdentity(LookupReport.class.getResourceAsStream("/lookup_build.properties"));

    private LookupReport() { }

    static String create(String item, String query, String find, String method, String area,
                         String view, String detailSource, MonsterLocationPanel.State detail)
    {
        StringBuilder text = new StringBuilder("Obtainable report\n");
        line(text, "Item", item);
        line(text, "Item query", query);
        line(text, "Find source", find);
        line(text, "Method", method);
        line(text, "NPC area", area);
        line(text, "View", view);
        if (detail != null)
        {
            line(text, "Detail source", detailSource);
            line(text, "Detail method", "NPC Drop");
            line(text, "Detail area", String.valueOf(detail.wilderness));
            line(text, "Detail membership", String.valueOf(detail.membership));
            line(text, "Other locations", detail.allLocations ? "Shown" : "Hidden");
        }
        return text.append(IDENTITY).append('\n').toString();
    }

    private static void line(StringBuilder text, String label, String value)
    {
        if (value != null && !value.trim().isEmpty())
            text.append(label).append(": ").append(value.replaceAll("[\\p{Cntrl}]+", " ").trim()).append('\n');
    }

    static String readIdentity(InputStream input)
    {
        try (InputStream stream = input)
        {
            if (stream == null) return "Build identity: unavailable";
            Properties data = new Properties();
            data.load(stream);
            String build = data.getProperty("build", "");
            String digest = data.getProperty("datasetSha256", "");
            if (data.size() != 5 || !build.matches("[a-zA-Z0-9][a-zA-Z0-9._/-]{1,100}")
                    || !digest.matches("[a-f0-9]{64}")
                    || !"1".equals(data.getProperty("accessSchema"))
                    || !"1".equals(data.getProperty("acquisitionSchema"))
                    || !"1".equals(data.getProperty("locationsSchema"))) return "Build identity: unavailable";
            return "Build: " + build + "\nData schemas: access=1; acquisition=1; locations=1\nDataset: " + digest.substring(0, 16);
        }
        catch (Exception ex)
        {
            return "Build identity: unavailable";
        }
    }
}
