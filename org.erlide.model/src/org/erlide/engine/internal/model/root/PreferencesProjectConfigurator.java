package org.erlide.engine.internal.model.root;

import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.erlide.engine.model.root.ErlangProjectProperties;
import org.erlide.engine.model.root.OTPProjectConfigurator;
import org.erlide.engine.model.root.PathSerializer;
import org.erlide.engine.model.root.ProjectConfigurator;
import org.erlide.engine.model.root.ProjectPreferencesConstants;
import org.erlide.runtime.runtimeinfo.RuntimeVersion;
import org.erlide.util.ErlLogger;
import org.osgi.service.prefs.BackingStoreException;

public class PreferencesProjectConfigurator implements ProjectConfigurator {

    private final IEclipsePreferences node;

    public PreferencesProjectConfigurator(final IEclipsePreferences node) {
        this.node = node;
    }

    public PreferencesProjectConfigurator() {
        this(null);
        // TODO this(BuilderConfigType.INTERNAL.getConfigName());
    }

    @Override
    public ErlangProjectProperties getConfiguration() {
        final ErlangProjectProperties result = new ErlangProjectProperties();
        if (node == null) {
            ErlLogger
                    .warn("Could not load project preferences from 'null', returning default values. ");
            return new OTPProjectConfigurator().getConfiguration();
        }

        // TODO node.addListener(project);

        final String sourceDirsStr = node.get(
                ProjectPreferencesConstants.SOURCE_DIRS,
                ProjectPreferencesConstants.DEFAULT_SOURCE_DIRS);
        result.setSourceDirs(PathSerializer.unpackList(sourceDirsStr));
        final String includeDirsStr = node.get(
                ProjectPreferencesConstants.INCLUDE_DIRS,
                ProjectPreferencesConstants.DEFAULT_INCLUDE_DIRS);
        result.setIncludeDirs(PathSerializer.unpackList(includeDirsStr));
        final String outputDirsStr = node.get(
                ProjectPreferencesConstants.OUTPUT_DIR,
                ProjectPreferencesConstants.DEFAULT_OUTPUT_DIR);
        result.setOutputDir(new Path(outputDirsStr));
        result.setRequiredRuntimeVersion(RuntimeVersion.Serializer.parse(node
                .get(ProjectPreferencesConstants.RUNTIME_VERSION, null)));
        if (!result.getRequiredRuntimeVersion().isDefined()) {
            result.setRequiredRuntimeVersion(ProjectPreferencesConstants.FALLBACK_RUNTIME_VERSION);
        }
        result.setExternalModulesFile(node.get(
                ProjectPreferencesConstants.PROJECT_EXTERNAL_MODULES,
                ProjectPreferencesConstants.DEFAULT_EXTERNAL_MODULES));
        result.setExternalIncludesFile(node.get(
                ProjectPreferencesConstants.EXTERNAL_INCLUDES,
                ProjectPreferencesConstants.DEFAULT_EXTERNAL_INCLUDES));
        return result;
    }

    @Override
    public void setConfiguration(final ErlangProjectProperties info) {
        if (node == null) {
            ErlLogger.warn("Could not store project preferences to 'null'");
            return;
        }

        node.put(ProjectPreferencesConstants.SOURCE_DIRS,
                PathSerializer.packList(info.getSourceDirs()));
        node.put(ProjectPreferencesConstants.INCLUDE_DIRS,
                PathSerializer.packList(info.getIncludeDirs()));
        node.put(ProjectPreferencesConstants.OUTPUT_DIR, info.getOutputDir()
                .toPortableString());
        node.put(ProjectPreferencesConstants.EXTERNAL_INCLUDES,
                info.getExternalIncludesFile());
        if (info.getRequiredRuntimeVersion().isDefined()) {
            node.put(ProjectPreferencesConstants.RUNTIME_VERSION, info
                    .getRequiredRuntimeVersion().asMinor().toString());
        } else {
            node.remove(ProjectPreferencesConstants.RUNTIME_VERSION);
        }
        node.put(ProjectPreferencesConstants.PROJECT_EXTERNAL_MODULES,
                info.getExternalModulesFile());

        try {
            node.flush();
        } catch (final BackingStoreException e) {
            ErlLogger.warn(e);
        }
    }
}
