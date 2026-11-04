package com.itemdroplookup.ui;

/** Offline packaging probe; run with the candidate JAR before test classes on the classpath. */
public final class PackagedFontProbe
{
    public static void main(String[] args) throws Exception
    {
        String origin = LookupStyle.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if (!origin.endsWith(".jar")) throw new IllegalStateException("Expected packaged code: " + origin);
        if (!"Atkinson Hyperlegible".equals(new LookupStyle(LookupStyle.STANDARD_SIZE).font().getFamily())) throw new IllegalStateException("Wrong font");
        for (String resource : new String[] {"/fonts/OFL.txt", "/THIRD_PARTY_NOTICES.txt"})
        {
            try (java.io.InputStream input = LookupStyle.class.getResourceAsStream(resource))
            {
                if (input == null || input.readAllBytes().length == 0) throw new IllegalStateException(resource);
            }
        }
        System.out.println("Packaged " + LookupStyle.fontName() + " instantiated; notices loaded; origin=" + origin);
    }
}
