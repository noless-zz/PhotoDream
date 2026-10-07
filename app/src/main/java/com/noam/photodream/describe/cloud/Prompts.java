package com.noam.photodream.describe.cloud;

/** The instruction sent with the photo. Kept in one place so all services ask the same thing. */
final class Prompts {

    private Prompts() { }

    static String forLanguage(String lang) {
        if ("he".equals(lang)) {
            return "תאר את התמונה במשפט קצר אחד בעברית, כך שאפשר להבחין בה מתמונות אחרות "
                    + "(מה רואים, צבעים, מקום). בלי שמות של אנשים ובלי הקדמה. החזר רק את המשפט.";
        }
        return "Describe this photo in one short, plain sentence that helps tell it apart from other photos "
                + "(what is in it, colors, place). No people's names and no preface. Reply with the sentence only.";
    }

    /** Models sometimes wrap the sentence in quotes; drop those and extra spaces. */
    static String clean(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        boolean straight = t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"");
        boolean curly = t.length() >= 2 && t.startsWith("“") && t.endsWith("”");
        if (straight || curly) t = t.substring(1, t.length() - 1).trim();
        return t;
    }
}
