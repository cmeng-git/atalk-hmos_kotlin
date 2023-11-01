/**
 *
 * Copyright 2017 Paul Schaub
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
package org.jivesoftware.smackx.jingle.transports.jingle_s5b.provider;

import static org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream.Mode.tcp;
import static org.jivesoftware.smackx.bytestreams.socks5.packet.Bytestream.Mode.udp;
import static org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate.ATTR_CID;
import static org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate.ATTR_HOST;
import static org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate.ATTR_JID;
import static org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate.ATTR_PORT;
import static org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate.ATTR_PRIORITY;
import static org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate.ATTR_TYPE;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.ParserUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.jingle.element.JingleContentTransportCandidate;
import org.jivesoftware.smackx.jingle.provider.JingleContentTransportProvider;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransport;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportCandidate;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportInfo;
import org.jivesoftware.smackx.jingle.transports.jingle_s5b.elements.JingleS5BTransportInfo.JingleS5BCandidateTransportInfo;

import java.io.IOException;

/**
 * Provider for JingleSocks5BytestreamTransport elements.
 */
public class JingleS5BTransportProvider extends JingleContentTransportProvider<JingleS5BTransport> {

    @Override
    public JingleS5BTransport parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment) throws XmlPullParserException, IOException {
        JingleS5BTransport.Builder builder = JingleS5BTransport.getBuilder();

        String streamId = parser.getAttributeValue(null, JingleS5BTransport.ATTR_SID);
        builder.setStreamId(streamId);

        String dstAddr = parser.getAttributeValue(null, JingleS5BTransport.ATTR_DSTADDR);
        builder.setDestinationAddress(dstAddr);

        String mode = parser.getAttributeValue(null, JingleS5BTransport.ATTR_MODE);
        if (mode != null) {
            builder.setMode(mode.equals(udp.toString()) ? udp : tcp);
        }

        JingleS5BTransportCandidate.Builder cb;
        outerloop: while (true) {
            XmlPullParser.TagEvent tag = parser.nextTag();
            switch (tag) {
                case START_ELEMENT: {
                    String name = parser.getName();
                    switch (name) {

                        case JingleContentTransportCandidate.ELEMENT:
                            cb = JingleS5BTransportCandidate.getBuilder();
                            cb.setCandidateId(parser.getAttributeValue(null, ATTR_CID));
                            cb.setHost(ParserUtils.getInternetAddressIngoringZoneIdAttribute(parser, ATTR_HOST));
                            cb.setJid(parser.getAttributeValue(null, ATTR_JID));
                            cb.setPriority(Integer.parseInt(parser.getAttributeValue(null, ATTR_PRIORITY)));

                            String portString = parser.getAttributeValue(null, ATTR_PORT);
                            if (portString != null) {
                                cb.setPort(Integer.parseInt(portString));
                            }

                            String typeString = parser.getAttributeValue(null, ATTR_TYPE);
                            if (typeString != null) {
                                cb.setType(JingleS5BTransportCandidate.Type.fromString(typeString));
                            }
                            builder.addTransportCandidate(cb.build());
                            break;

                        case JingleS5BTransportInfo.CandidateActivated.ELEMENT:
                            builder.setTransportInfo(new JingleS5BTransportInfo.CandidateActivated(
                                    parser.getAttributeValue(null,
                                            JingleS5BCandidateTransportInfo.ATTR_CID)));
                            break;

                        case JingleS5BTransportInfo.CandidateUsed.ELEMENT:
                            builder.setTransportInfo(new JingleS5BTransportInfo.CandidateUsed(
                                    parser.getAttributeValue(null,
                                            JingleS5BCandidateTransportInfo.ATTR_CID)));
                            break;

                        case JingleS5BTransportInfo.CandidateError.ELEMENT:
                            builder.setTransportInfo(JingleS5BTransportInfo.CandidateError.INSTANCE);
                            break;

                        case JingleS5BTransportInfo.ProxyError.ELEMENT:
                            builder.setTransportInfo(JingleS5BTransportInfo.ProxyError.INSTANCE);
                            break;
                    }
                }
                break;

                case END_ELEMENT: {
                    if (parser.getDepth() == initialDepth) {
                            break outerloop;
                    }
                }
                    break;
            }
        }
        return builder.build();
    }
}
