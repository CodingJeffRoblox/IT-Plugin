package com.itplugin.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ConfigValidator {

    public record ValidationResult(File file, List<String> errors, List<String> warnings) {
        public boolean hasIssues() { return !errors.isEmpty() || !warnings.isEmpty(); }
    }

    /**
     * Scans all *.yml files in all plugin subdirectories under the given root.
     */
    public static List<ValidationResult> validatePluginConfigs(File pluginsDir, boolean reportWarnings) {
        List<ValidationResult> results = new ArrayList<>();

        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) return results;

        // Each direct child of plugins/ is a plugin folder
        File[] pluginDirs = pluginsDir.listFiles(File::isDirectory);
        if (pluginDirs == null) return results;

        for (File dir : pluginDirs) {
            File[] ymls = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".yml"));
            if (ymls == null) continue;
            for (File yml : ymls) {
                ValidationResult result = validateYml(yml, reportWarnings);
                if (result.hasIssues()) results.add(result);
            }
        }

        return results;
    }

    /**
     * Validate a single YAML file for parse errors and common config issues.
     */
    public static ValidationResult validateYml(File file, boolean reportWarnings) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!file.exists()) {
            errors.add("File does not exist: " + file.getPath());
            return new ValidationResult(file, errors, warnings);
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (InvalidConfigurationException e) {
            errors.add("YAML parse error: " + e.getMessage().split("\n")[0]);
            return new ValidationResult(file, errors, warnings);
        } catch (Exception e) {
            errors.add("Cannot read file: " + e.getMessage());
            return new ValidationResult(file, errors, warnings);
        }

        if (reportWarnings) {
            // Warn about empty config
            if (config.getKeys(false).isEmpty()) {
                warnings.add("Config is empty (no keys found)");
            }

            // Warn about keys with null values
            for (String key : config.getKeys(true)) {
                if (config.get(key) == null) {
                    warnings.add("Key '" + key + "' has a null value");
                }
            }

            // Warn about suspiciously large string values (potential dump)
            for (String key : config.getKeys(true)) {
                Object val = config.get(key);
                if (val instanceof String s && s.length() > 2000) {
                    warnings.add("Key '" + key + "' has an unusually long value (" + s.length() + " chars)");
                }
            }
        }

        return new ValidationResult(file, errors, warnings);
    }
}
