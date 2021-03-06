/*******************************************************************************
 * Copyright (c) 2013 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available
 * at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.core.internal.builder;

import static com.google.common.collect.Lists.newArrayList;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.osgi.util.NLS;
import org.erlide.backend.BackendCore;
import org.erlide.backend.api.BackendException;
import org.erlide.backend.api.IBackend;
import org.erlide.core.builder.BuildResource;
import org.erlide.core.builder.BuilderHelper;
import org.erlide.core.builder.BuilderHelper.SearchVisitor;
import org.erlide.core.builder.CompilerOptions;
import org.erlide.engine.ErlangEngine;
import org.erlide.engine.model.IErlModel;
import org.erlide.engine.model.builder.BuilderProperties;
import org.erlide.engine.model.builder.ErlangBuilder;
import org.erlide.engine.model.builder.MarkerUtils;
import org.erlide.engine.model.root.ErlangProjectProperties;
import org.erlide.engine.model.root.IErlProject;
import org.erlide.runtime.rpc.IRpcFuture;
import org.erlide.util.ErlLogger;

import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class InternalBuilder extends ErlangBuilder {

    BuildNotifier notifier;
    private final BuilderHelper helper = new BuilderHelper();

    @Override
    public IProject[] build(final int kind, final Map<String, String> args,
            final IProgressMonitor monitor) throws CoreException {

        super.build(kind, args, monitor);

        final long time = System.currentTimeMillis();
        final IProject project = getProject();
        if (project == null || !project.isAccessible()) {
            return null;
        }

        // if (BuilderHelper.isDebugging()) {
        ErlLogger.trace("build",
                "Start " + project.getName() + ": " + helper.buildKind(kind));
        // }
        final IErlProject erlProject = ErlangEngine.getInstance().getModel()
                .getErlangProject(project);
        try {
            initializeBuilder(monitor);

            // TODO validate source and include directories
            final ErlangProjectProperties properties = erlProject.getProperties();
            final IPath out = properties.getOutputDir();
            final IResource outr = project.findMember(out);
            if (outr != null) {
                try {
                    outr.setDerived(true, null);
                    outr.refreshLocal(IResource.DEPTH_ZERO, null);
                } catch (final CoreException e) {
                    // ignore it
                }
            }

            handleAppFile(getProject().getLocation().toPortableString() + "/" + out,
                    properties.getSourceDirs());

            handleErlangFiles(erlProject, project, args, kind, getDelta(project));

        } catch (final OperationCanceledException e) {
            if (BuilderHelper.isDebugging()) {
                ErlLogger.debug("Build of " + project.getName() + " was canceled.");
            }
        } catch (final Exception e) {
            ErlLogger.error(e);
            final String msg = NLS.bind(BuilderMessages.build_inconsistentProject,
                    e.getLocalizedMessage(), e.getClass().getName());
            MarkerUtils.addProblemMarker(project, null, null, msg, 0,
                    IMarker.SEVERITY_ERROR);
        } finally {
            cleanup();
            // if (BuilderHelper.isDebugging()) {
            ErlLogger.trace(
                    "build",
                    " Done " + project.getName() + " took "
                            + Long.toString(System.currentTimeMillis() - time));
            // }
        }
        return null;
    }

    @Override
    public void clean(final IProgressMonitor monitor) {
        final IProject currentProject = getProject();
        if (currentProject == null || !currentProject.isAccessible()) {
            return;
        }

        if (BuilderHelper.isDebugging()) {
            ErlLogger.trace("build", "Cleaning " + currentProject.getName() //$NON-NLS-1$
                    + " @ " + new Date(System.currentTimeMillis()));
        }

        try {
            initializeBuilder(monitor);
            MarkerUtils.removeProblemMarkersFor(currentProject);
            final IErlProject erlProject = ErlangEngine.getInstance().getModel()
                    .getErlangProject(currentProject);
            final IFolder bf = currentProject.getFolder(erlProject.getProperties()
                    .getOutputDir());
            if (bf.exists()) {
                cleanupOutput(bf, monitor);
            }

        } catch (final Exception e) {
            ErlLogger.error(e);
            final String msg = NLS.bind(BuilderMessages.build_inconsistentProject,
                    e.getLocalizedMessage(), e.getClass().getName());
            MarkerUtils.addProblemMarker(currentProject, null, null, msg, 0,
                    IMarker.SEVERITY_ERROR);
        } finally {
            cleanup();
            if (BuilderHelper.isDebugging()) {
                ErlLogger.debug("Finished cleaning " + currentProject.getName() //$NON-NLS-1$
                        + " @ " + new Date(System.currentTimeMillis()));
            }
        }
    }

    private void cleanupOutput(final IFolder folder, final IProgressMonitor monitor)
            throws CoreException {
        final IResource[] beams = folder.members();
        monitor.beginTask("Cleaning Erlang files", beams.length);
        if (beams.length > 0) {
            final float delta = 100.0f / beams.length;
            for (final IResource element : beams) {
                if ("beam".equals(element.getFileExtension())) {
                    final IResource source = findCorrespondingSource(element);
                    if (source != null) {
                        element.delete(true, monitor);
                    }
                    notifier.updateProgressDelta(delta);
                }
                if ("app".equals(element.getFileExtension())) {
                    final IResource source = findCorrespondingSource(element);
                    if (source != null) {
                        element.delete(true, monitor);
                    }
                }
            }
        }
    }

    private void handleErlangFiles(final IErlProject erlProject, final IProject project,
            final Map<String, String> args, final int kind,
            final IResourceDelta resourceDelta) throws CoreException, BackendException {
        final OtpErlangList compilerOptions = CompilerOptions.get(project);

        final Set<BuildResource> resourcesToBuild = getResourcesToBuild(kind, args,
                project, resourceDelta);
        final int n = resourcesToBuild.size();
        // if (BuilderHelper.isDebugging()) {
        ErlLogger.debug("Will compile %d resource(s)", Integer.valueOf(n));
        // }
        if (n == 0) {
            return;
        }
        final IBackend backend = BackendCore.getBackendManager().getBuildBackend(project);
        if (backend == null) {
            final String message = "No backend with the required "
                    + "version could be found. Can't build.";
            MarkerUtils.addProblemMarker(project, null, null, message, 0,
                    IMarker.SEVERITY_ERROR);
            throw new BackendException(message);
        }
        final IErlModel model = ErlangEngine.getInstance().getModel();
        backend.addProjectPath(model.findProject(project));

        notifier.setProgressPerCompilationUnit(1.0f / n);
        final Map<IRpcFuture, IResource> results = new HashMap<IRpcFuture, IResource>();
        for (final BuildResource bres : resourcesToBuild) {
            notifier.checkCancel();
            final IResource resource = bres.getResource();
            MarkerUtils.deleteMarkers(resource);
            // notifier.aboutToCompile(resource);
            if ("erl".equals(resource.getFileExtension())) {
                final String outputDir = erlProject.getProperties().getOutputDir()
                        .toString();
                final IRpcFuture f = helper.startCompileErl(project, bres, outputDir,
                        backend.getRpcSite(), compilerOptions,
                        kind == IncrementalProjectBuilder.FULL_BUILD);
                if (f != null) {
                    results.put(f, resource);
                }
            } else if ("yrl".equals(resource.getFileExtension())) {
                final IRpcFuture f = helper.startCompileYrl(project, resource,
                        backend.getRpcSite(), compilerOptions);
                if (f != null) {
                    results.put(f, resource);
                }
            } else {
                ErlLogger.warn("Don't know how to compile: %s", resource.getName());
            }
        }

        final List<Entry<IRpcFuture, IResource>> done = Lists.newArrayList();
        final List<Entry<IRpcFuture, IResource>> waiting = Lists.newArrayList(results
                .entrySet());

        // TODO should use some kind of notification!
        while (!waiting.isEmpty()) {
            for (final Entry<IRpcFuture, IResource> result : waiting) {
                notifier.checkCancel();
                OtpErlangObject r;
                try {
                    r = result.getKey().get(100, TimeUnit.MILLISECONDS);
                } catch (final Exception e) {
                    r = null;
                }
                if (r != null) {
                    final IResource resource = result.getValue();

                    helper.completeCompile(project, resource, r, backend.getRpcSite(),
                            compilerOptions);
                    notifier.compiled(resource);

                    done.add(result);
                }
            }
            waiting.removeAll(done);
            done.clear();
        }
        helper.refreshOutputDir(project);

        try {
            helper.checkForClashes(backend.getRpcSite(), project);
        } catch (final Exception e) {
        }
        backend.removeProjectPath(model.findProject(project));

    }

    private void handleAppFile(final String outPath, final Collection<IPath> sources) {

        // bad idea to traverse every source dir at every build!
        // what to do instead?

        // final Collection<String> srcPaths = Collections2.transform(sources,
        // new Function<IPath, String>() {
        // @Override
        // public String apply(final IPath input) {
        // final IFolder dir = (IFolder) getProject().findMember(input);
        // return dir.getLocation().toPortableString();
        // }
        // });
        final Collection<String> srcPaths = newArrayList();
        for (final IPath src : sources) {
            final IFolder dir = (IFolder) getProject().findMember(src);
            if (dir == null) {
                continue;
            }
            try {
                for (final IResource file : dir.members()) {
                    final String name = file.getName();
                    if (name.endsWith(".app.src")) {
                        final String appSrc = file.getLocation().toPortableString();
                        final String destPath = outPath + "/"
                                + name.substring(0, name.lastIndexOf('.'));
                        fillAppFileDetails(appSrc, destPath, srcPaths);
                    }
                }
            } catch (final CoreException e) {
                ErlLogger.error(e);
            }
        }

    }

    private void fillAppFileDetails(final String appSrc, final String destPath,
            final Collection<String> sources) {
        try {
            final IBackend backend = BackendCore.getBackendManager().getBuildBackend(
                    getProject());
            backend.getRpcSite().call("erlide_builder", "compile_app_src", "ssls",
                    appSrc, destPath, sources);
        } catch (final Exception e) {
            ErlLogger.error(e);
        }

    }

    private void initializeBuilder(final IProgressMonitor monitor) {
        final IProject currentProject = getProject();
        notifier = new BuildNotifier(monitor, currentProject);
        notifier.begin();
    }

    private void cleanup() {
        notifier.done();
        notifier = null;
    }

    private Set<BuildResource> getResourcesToBuild(final int kind,
            @SuppressWarnings("rawtypes") final Map args, final IProject currentProject,
            final IResourceDelta resourceDelta) throws CoreException {
        Set<BuildResource> resourcesToBuild = Sets.newHashSet();
        final IProgressMonitor submon = new SubProgressMonitor(notifier.fMonitor, 10);
        submon.beginTask("retrieving resources to build", IProgressMonitor.UNKNOWN);
        if (kind == IncrementalProjectBuilder.FULL_BUILD) {
            resourcesToBuild = helper.getAffectedResources(args, currentProject, submon);
        } else {
            final IResourceDelta delta = resourceDelta;
            final Path path = new Path(".settings/org.erlide.core.prefs");
            if (delta != null && delta.findMember(path) != null) {
                ErlLogger.info("project configuration changed: doing full rebuild");
                resourcesToBuild = helper.getAffectedResources(args, currentProject,
                        submon);
            } else {
                resourcesToBuild = helper.getAffectedResources(args, delta, submon);
            }
        }
        submon.done();
        return resourcesToBuild;
    }

    public IResource findCorrespondingSource(final IResource beam) throws CoreException {
        final String[] p = beam.getName().split("\\.");
        final SearchVisitor searcher = helper.new SearchVisitor(p[0], null);
        beam.getProject().accept(searcher);
        final IResource source = searcher.getResult();
        return source;
    }

    @Override
    public BuilderProperties getProperties() {
        return null;
    }

}
