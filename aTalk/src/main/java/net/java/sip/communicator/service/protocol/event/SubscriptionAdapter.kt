/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event

/**
 * Represents a default implementation of `SubscriptionListener` which performs no
 * processing of the received events and allows extenders to easily implement the interface in
 * question by just overriding the methods they are interested in.
 *
 * @author Lubomir Marinov
 */
class SubscriptionAdapter : SubscriptionListener {
    /*
	 * Implements SubscriptionListener#contactModified(ContactPropertyChangeEvent). Does nothing.
	 */
    override fun contactModified(evt: ContactPropertyChangeEvent?) {}

    /*
	 * Implements SubscriptionListener#subscriptionCreated(SubscriptionEvent). Does nothing.
	 */
    override fun subscriptionCreated(evt: SubscriptionEvent?) {}

    /*
	 * Implements SubscriptionListener#subscriptionFailed(SubscriptionEvent). Does nothing.
	 */
    override fun subscriptionFailed(evt: SubscriptionEvent?) {}

    /*
	 * Implements SubscriptionListener#subscriptionMoved(SubscriptionMovedEvent). Does nothing.
	 */
    override fun subscriptionMoved(evt: SubscriptionMovedEvent?) {}

    /*
	 * Implements SubscriptionListener#subscriptionRemoved(SubscriptionEvent). Does nothing.
	 */
    override fun subscriptionRemoved(evt: SubscriptionEvent?) {}

    /*
	 * Implements SubscriptionListener#subscriptionResolved(SubscriptionEvent). Does nothing.
	 */
    override fun subscriptionResolved(evt: SubscriptionEvent?) {}
}