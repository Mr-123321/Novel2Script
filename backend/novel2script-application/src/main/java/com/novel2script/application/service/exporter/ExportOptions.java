package com.novel2script.application.service.exporter;

/**
 * Immutable configuration for YAML export behaviour.
 *
 * <p>Use {@link #defaults()} for the recommended production settings.
 */
public record ExportOptions(
        /** When true, include scene storyboard lines in the output. */
        boolean includeStoryboard,

        /** When true, include a {@code meta} section with counts and generation info. */
        boolean includeMetadata,

        /** When true, format YAML with indentation-friendly block style and flow mapping for small values. */
        boolean prettyPrint,

        /** When true, run {@link SchemaValidator} before writing and reject invalid output. */
        boolean validateSchema,

        /** When true, omit YAML keys whose value is {@code null}, empty collection, or blank string. */
        boolean compressEmpty
) {

    /**
     * Sensible defaults suitable for production use.
     * <ul>
     *   <li>Storyboard lines are included</li>
     *   <li>Metadata header is included</li>
     *   <li>Pretty-print is on</li>
     *   <li>Schema validation is on</li>
     *   <li>Empty values are NOT compressed away</li>
     * </ul>
     */
    public static ExportOptions defaults() {
        return new ExportOptions(true, true, true, true, false);
    }
}
