package com.submarine.template;

import java.util.Map;

/** Lookup from {@link com.submarine.data.SubmarineMetadata#templateId()} to its {@link SubmarineTemplate}. */
public final class SubmarineTemplates {
    private static final Map<String, SubmarineTemplate> BY_ID = Map.of(
            StarterSubmarineTemplate.ID, StarterSubmarineTemplate.TEMPLATE,
            OceanPearlSubmarineBuilder.ID, OceanPearlSubmarineBuilder.TEMPLATE
    );

    private SubmarineTemplates() {
    }

    public static SubmarineTemplate get(String id) {
        SubmarineTemplate template = BY_ID.get(id);
        if (template == null) {
            throw new IllegalStateException("Unknown submarine template id: " + id);
        }
        return template;
    }
}
