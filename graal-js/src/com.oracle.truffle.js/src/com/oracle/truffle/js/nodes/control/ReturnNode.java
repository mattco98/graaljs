/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.control;

import java.util.Set;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.access.JSConstantNode;
import com.oracle.truffle.js.nodes.instrumentation.JSTags;

/**
 * 12.9 The return Statement.
 */
@NodeInfo(shortName = "return")
public class ReturnNode extends StatementNode {

    @Child protected JavaScriptNode expression;

    protected ReturnNode(JavaScriptNode expression) {
        this.expression = expression;
    }

    public static ReturnNode create(JavaScriptNode expression) {
        if (expression instanceof JSConstantNode) {
            return new ConstantReturnNode(expression);
        } else {
            assert !(expression instanceof EmptyNode);
            return new ReturnNode(expression);
        }
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == JSTags.ControlFlowBranchTag.class) {
            return true;
        }
        return super.hasTag(tag);
    }

    @Override
    public Object getNodeObject() {
        return JSTags.createNodeObjectDescriptor("type", JSTags.ControlFlowBranchTag.Type.Return.name());
    }

    public static ReturnNode createFrameReturn(JavaScriptNode expression) {
        return new FrameReturnNode(expression);
    }

    public static ReturnNode createTerminalPositionReturn(JavaScriptNode expression) {
        return new TerminalPositionReturnNode(expression);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        throw new ReturnException(expression.execute(frame));
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new ReturnException(expression.execute(frame));
    }

    @Override
    protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
        ReturnNode copy = (ReturnNode) copy();
        copy.expression = cloneUninitialized(expression, materializedTags);
        return copy;
    }

    @Override
    public boolean isResultAlwaysOfType(Class<?> clazz) {
        return expression.isResultAlwaysOfType(clazz);
    }

    private static class ConstantReturnNode extends ReturnNode {

        private final ReturnException exception;

        protected ConstantReturnNode(JavaScriptNode expression) {
            super(expression);
            this.exception = new ReturnException(expression.execute(null));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw exception;
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            throw exception;
        }
    }

    public static class FrameReturnNode extends ReturnNode {

        private static final ReturnException RETURN_EXCEPTION = new ReturnException(null);

        protected FrameReturnNode(JavaScriptNode expression) {
            super(expression);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            expression.executeVoid(frame);
            throw RETURN_EXCEPTION;
        }

        @Override
        public void executeVoid(VirtualFrame frame) {
            expression.executeVoid(frame);
            throw RETURN_EXCEPTION;
        }
    }

    public static class TerminalPositionReturnNode extends ReturnNode {

        protected TerminalPositionReturnNode(JavaScriptNode expression) {
            super(expression);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return expression.execute(frame);
        }
    }

}
