/*******************************************************************************
 * Copyright (c) 2010 György Orosz.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     György Orosz - initial API and implementation
 ******************************************************************************/
package org.erlide.wrangler.refactoring.duplicatedcode.core;

import java.util.List;

import org.erlide.wrangler.refactoring.duplicatedcode.ui.elements.DuplicatedCodeElement;

import com.ericsson.otp.erlang.OtpErlangObject;

public abstract class AbstractDuplicatesParser implements IResultParser {

	protected String errorMessage;
	protected boolean isSuccessful;
	protected List<DuplicatedCodeElement> duplicates;

	public AbstractDuplicatesParser(OtpErlangObject obj) {
		parse(obj);
	}

	public List<DuplicatedCodeElement> getDuplicates() {
		return duplicates;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isSuccessful() {
		return isSuccessful;
	}

	protected void setUnSuccessful(String errorMessage) {
		isSuccessful = false;
		this.errorMessage = errorMessage;
	}
}
