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
package org.erlide.wrangler.refactoring.util;

import com.ericsson.otp.erlang.OtpErlangTuple;

public interface IRange {
	public int getStartLine();

	public int getEndLine();

	public int getStartCol();

	public int getEndCol();

	public OtpErlangTuple getStartPos();

	public OtpErlangTuple getEndPos();

	public OtpErlangTuple getPos();

	public String toString();

}
