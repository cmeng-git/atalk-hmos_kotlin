/**
 *
 * Copyright 2017-2022 Paul Schaub
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.jingle.component;

import org.jivesoftware.smackx.bytestreams.BytestreamSession;

import java.io.IOException;

/**
 * Jingle Session to handle Security ByteStream session.
 *
 * @author Paul Schaub
 * @author Eng Chong Meng
 */
public abstract class JingleSecurityBytestreamSession implements BytestreamSession {

    protected BytestreamSession wrapped;

    @Override
    public int getReadTimeout() throws IOException {
        return wrapped.getReadTimeout();
    }

    @Override
    public void setReadTimeout(int timeout) throws IOException {
        wrapped.setReadTimeout(timeout);
    }

    public JingleSecurityBytestreamSession(BytestreamSession session) {
        this.wrapped = session;
    }
}
