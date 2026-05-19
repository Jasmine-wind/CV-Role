package com.winter.airesumeoptimizer.infra.storage;

import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SafeFilenameGenerator {

    public String generate(String filename) {
        String cleanFilename = StringUtils.cleanPath(filename == null ? "file" : filename);
        int slashIndex = Math.max(cleanFilename.lastIndexOf('/'), cleanFilename.lastIndexOf('\\'));
        if (slashIndex >= 0) {
            cleanFilename = cleanFilename.substring(slashIndex + 1);
        }
        cleanFilename = cleanFilename.replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_")
                .toLowerCase(Locale.ROOT);
        if (cleanFilename.isBlank() || cleanFilename.equals(".") || cleanFilename.equals("..")) {
            return "file";
        }
        return cleanFilename;
    }
}
