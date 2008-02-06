/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.erlide.core.erlang.internal;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.erlide.basiccore.ErlLogger;
import org.erlide.core.erlang.ErlModelException;
import org.erlide.core.erlang.ErlModelStatus;
import org.erlide.core.erlang.ErlangCore;
import org.erlide.core.erlang.IErlElement;
import org.erlide.core.erlang.IErlModelManager;
import org.erlide.core.erlang.IErlModelStatusConstants;
import org.erlide.core.erlang.IOpenable;

/**
 * Abstract class for implementations of Erlang elements which are IOpenable.
 * 
 * @see IErlElement
 * @see IOpenable
 */
public abstract class Openable extends ErlElement implements IOpenable {

	protected IResource findResult;

	protected Openable(IErlElement parent, String name) {
		super(parent, name);
	}

	// /**
	// * The buffer associated with this element has changed. Registers this
	// * element as being out of synch with its buffer's contents. If the buffer
	// * has been closed, this element is set as NOT out of synch with the
	// * contents.
	// *
	// * @see IBufferChangedListener
	// */
	// public void bufferChanged(BufferChangedEvent event) {
	// if (event.getBuffer().isClosed()) {
	// ErlangCore.getModelManager().getElementsOutOfSynchWithBuffers()
	// .remove(this);
	// getBufferManager().removeBuffer(event.getBuffer());
	// } else {
	// ErlangCore.getModelManager().getElementsOutOfSynchWithBuffers()
	// .put(this, this);
	// }
	// }

	/**
	 * Builds this element's structure and properties in the given info object,
	 * based on this element's current contents (reuse buffer contents if this
	 * element has an open buffer, or resource contents if this element does not
	 * have an open buffer). Children are placed in the given newElements table
	 * (note, this element has already been placed in the newElements table).
	 * Returns true if successful, or false if an error is encountered while
	 * determining the structure of this element.
	 * 
	 * @param dirtyRegion
	 *            TODO
	 */
	protected abstract boolean buildStructure(IProgressMonitor pm,
			IResource underlyingResource) throws ErlModelException;

	/*
	 * Returns whether this element can be removed from the Erlang model cache
	 * to make space.
	 */
	public boolean canBeRemovedFromCache() {
		try {
			return !hasUnsavedChanges();
		} catch (final ErlModelException e) {
			return false;
		}
	}

	// /*
	// * Returns whether the buffer of this element can be removed from the
	// Erlang
	// * model cache to make space.
	// */
	// public boolean canBufferBeRemovedFromCache(IBuffer buffer) {
	// return !buffer.hasUnsavedChanges();
	// }

	// /**
	// * Close the buffer associated with this element, if any.
	// */
	// protected void closeBuffer() {
	// if (!hasBuffer()) {
	// return; // nothing to do
	// }
	// final IBuffer buffer = getBufferManager().getBuffer(this);
	// if (buffer != null) {
	// buffer.close();
	// buffer.removeBufferChangedListener(this);
	// }
	// }

	// /**
	// * This element is being closed. Do any necessary cleanup.
	// */
	// @Override
	// protected void closing(Object info) {
	// closeBuffer();
	// }

	/**
	 * @see IErlElement
	 */
	@Override
	public boolean exists() {
		final IErlModelManager manager = ErlangCore.getModelManager();
		if (manager.getInfo(this) != null) {
			return true;
		}
		if (!parentExists()) {
			return false;
		}
		// PackageFragmentRoot root = getPackageFragmentRoot();
		// if (root != null && (root == this || !root.isArchive()))
		// {
		// return resourceExists();
		// }
		return super.exists();
	}

	public void open(IProgressMonitor monitor) throws ErlModelException {
		// open the parent if necessary
		openParent(monitor);
		if (monitor != null && monitor.isCanceled()) {
			return;
		}

		// build the structure of the openable (this will open the buffer if
		// needed)
		try {
			if (!isStructureKnown()) {
				final boolean knownStructure = buildStructure(monitor,
						getResource());
				setIsStructureKnown(knownStructure);
			}
		} catch (final ErlModelException e) {
			throw e;
		}

		// remove out of sync buffer for this element
		ErlangCore.getModelManager().getElementsOutOfSynchWithBuffers().remove(
				this);
	}

	// /**
	// * Note: a buffer with no unsaved changes can be closed by the Erlang
	// Model
	// * since it has a finite number of buffers allowed open at one time. If
	// this
	// * is the first time a request is being made for the buffer, an attempt is
	// * made to create and fill this element's buffer. If the buffer has been
	// * closed since it was first opened, the buffer is re-created.
	// *
	// * @see IOpenable
	// */
	// public IBuffer getBuffer() {
	// if (hasBuffer()) {
	// // ensure element is open
	// IBuffer buffer = getBufferManager().getBuffer(this);
	// if (buffer == null) {
	// // try to (re)open a buffer
	// buffer = openBuffer(null, this);
	// }
	// return buffer;
	// }
	// return null;
	// }

	// /**
	// * Returns the buffer manager for this element.
	// */
	// protected BufferManager getBufferManager() {
	// return BufferManager.getDefaultBufferManager();
	// }

	/**
	 * Return my underlying resource. Elements that may not have a corresponding
	 * resource must override this method.
	 * 
	 * @see IErlElement
	 */
	public IResource getCorrespondingResource() throws ErlModelException {
		return getUnderlyingResource();
	}

	/*
	 * @see IErlElement
	 */
	public IOpenable getOpenable() {
		return this;
	}

	/**
	 * @see IErlElement
	 */
	public IResource getUnderlyingResource() throws ErlModelException {
		final IResource parentResource = fParent.getUnderlyingResource();
		if (parentResource == null) {
			return null;
		}
		final int type = parentResource.getType();
		if (type == IResource.FOLDER || type == IResource.PROJECT) {
			final IContainer topfolder = (IContainer) parentResource;

			// TODO use project properties
			final IResource resource = findFile(topfolder, fName);

			if (resource == null) {
				throw newNotPresentException();
			}
			return resource;
		}
		return parentResource;
	}

	class ErlangResourceVisitor implements IResourceVisitor {

		private final String aname;

		public ErlangResourceVisitor(String name) {
			aname = name;
		}

		public boolean visit(IResource resource) {
			if (resource.getType() == IResource.FILE
					&& resource.getName().equals(aname)) {
				findResult = resource;
				return false;
			}
			// return true to continue visiting children.
			return true;
		}
	}

	private IResource findFile(IContainer topfolder, String string) {
		final IResource result = topfolder.findMember(string);
		if (result != null) {
			return result;
		}

		findResult = null;
		final IResourceVisitor v = new ErlangResourceVisitor(string);
		try {
			topfolder.accept(v);
		} catch (final CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return findResult;
	}

	/**
	 * Returns true if this element may have an associated source buffer,
	 * otherwise false. Subclasses must override as required.
	 */
	protected boolean hasBuffer() {
		return false;
	}

	/**
	 * @see IOpenable
	 */
	@SuppressWarnings("unchecked")
	public boolean hasUnsavedChanges() throws ErlModelException {

		if (isReadOnly() || !isOpen()) {
			return false;
		}
		// final IBuffer buf = this.getBuffer();
		// if (buf != null && buf.hasUnsavedChanges()) {
		// return true;
		// }
		// for packages and projects must check open buffers
		// to see if they have an child with unsaved changes
		final ErlElementType elementType = getElementType();
		if (elementType == ErlElementType.PROJECT
				|| elementType == ErlElementType.MODEL) {
			// final Enumeration openBuffers =
			// getBufferManager().getOpenBuffers();
			// while (openBuffers.hasMoreElements()) {
			// final IBuffer buffer = (IBuffer) openBuffers.nextElement();
			// if (buffer.hasUnsavedChanges()) {
			// final IErlElement owner = (IErlElement) buffer.getOwner();
			// if (isAncestorOf(owner)) {
			// return true;
			// }
			// }
			// }
		}

		return false;
	}

	/**
	 * Subclasses must override as required.
	 * 
	 * @see IOpenable
	 */
	public boolean isConsistent() {
		return true;
	}

	/**
	 * 
	 * @see IOpenable
	 */
	public boolean isOpen() {
		return ErlangCore.getModelManager().getInfo(this) != null;
	}

	/**
	 * Returns true if this represents a source element. Openable source
	 * elements have an associated buffer created when they are opened.
	 */
	protected boolean isSourceElement() {
		return false;
	}

	/**
	 * @see IOpenable
	 */
	public void makeConsistent(IProgressMonitor monitor)
			throws ErlModelException {
		ErlLogger.debug("make consistent? ");
		if (isConsistent()) {
			return;
		}
		ErlLogger.debug("make consistent");
	}

	// /**
	// * Opens a buffer on the contents of this element, and returns the buffer,
	// * or returns <code>null</code> if opening fails. By default, do nothing -
	// * subclasses that have buffers must override as required.
	// */
	// protected IBuffer openBuffer(IProgressMonitor pm, Object info) {
	// return null;
	// }

	/**
	 * Open the parent element if necessary.
	 */
	protected void openParent(IProgressMonitor pm) throws ErlModelException {

		final Openable openableParent = (Openable) getOpenableParent();
		if (openableParent != null && !openableParent.isOpen()) {
			openableParent.open(pm);
		}
	}

	/**
	 * Answers true if the parent exists (null parent is answering true)
	 * 
	 */
	protected boolean parentExists() {

		final IErlElement parentElement = getParent();
		if (parentElement == null) {
			return true;
		}
		return parentElement.exists();
	}

	/**
	 * Returns whether the corresponding resource or associated file exists
	 */
	protected boolean resourceExists() {
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		if (workspace == null) {
			return false;
		}
		return ErlModel.getTarget(workspace.getRoot(), getResource()
				.getFullPath().makeRelative(), true) != null;
	}

	/**
	 * @see IOpenable
	 */
	public void save(IProgressMonitor pm, boolean force)
			throws ErlModelException {
		if (isReadOnly()) {
			throw new ErlModelException(new ErlModelStatus(
					IErlModelStatusConstants.READ_ONLY, this));
		}
		// final IBuffer buf = getBuffer();
		// if (buf != null) { // some Openables (like a ErlProject) don't have a
		// // buffer
		// buf.save(pm, force);
		// makeConsistent(pm); // update the element info of this
		// // element
		// }
	}
}
